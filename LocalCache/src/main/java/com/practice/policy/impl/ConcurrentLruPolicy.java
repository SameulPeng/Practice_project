package com.practice.policy.impl;

import com.practice.policy.CacheWrapper;
import com.practice.policy.EvictionPolicy;
import org.springframework.lang.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CAS并发LRU缓存淘汰策略控制器
 * @param <T> 缓存key的类型
 */
public class ConcurrentLruPolicy<T> implements EvictionPolicy<T> {
    private final AtomicInteger size  = new AtomicInteger(); // 缓存当前大小
    private final int capacity; // 缓存容量
    private final ExecutorService refreshPool = // 异步刷新线程池
            new ThreadPoolExecutor(
                    2, 8, 60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(1024), r -> new Thread(r,"AsyncRefreshHandler"),
                    // 拒绝策略为调用者自己执行
                    new ThreadPoolExecutor.CallerRunsPolicy());
    private final VarHandle PREV; // 前指针变量句柄
    private final VarHandle NEXT; // 后指针变量句柄
    private final VarHandle MIDPOINT; // 中间节点指针变量句柄
    private final LruCacheNode head; // 链表头部节点的前驱节点，作为指示节点
    private final LruCacheNode tail; // 链表尾部节点的后继节点，作为指示节点
    private LruCacheNode midpoint; // 链表中间节点
    private volatile int midpointLock; // 链表中间节点指针的乐观锁
    private volatile int tailPrevLockReentrant; // 链表尾部指示节点的前指针的乐观锁的重入标志

    public ConcurrentLruPolicy(int capacity) throws NoSuchFieldException, IllegalAccessException {
        this.capacity = capacity;
        this.head = new LruCacheNode(null, null);
        this.tail = new LruCacheNode(null, null);
        this.head.next = this.tail;
        this.tail.prev = this.head;
        this.midpoint = this.tail;
        // 创建变量句柄类，用于实现CAS操作
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        this.PREV = lookup.findVarHandle(LruCacheNode.class, "prevLock", int.class);
        this.NEXT = lookup.findVarHandle(LruCacheNode.class, "nextLock", int.class);
        this.MIDPOINT = lookup.findVarHandle(ConcurrentLruPolicy.class, "midpointLock", int.class);
    }

    /**
     * 缓存节点类
     */
    public class LruCacheNode implements CacheWrapper<T> {
        private LruCacheNode next; // 指向后继节点的指针
        private LruCacheNode prev; // 指向前驱节点的指针
        private int state = 1; // 表示缓存节点的数据的冷热状态，中间节点之前的节点的状态为1，中间节点之后的节点的状态为0，中间节点可能是两种状态之中的一种
        private final String key; // 缓存key
        private final T value; // 缓存value
        private volatile int prevLock = 0; // 前指针的乐观锁
        private volatile int nextLock = 0; // 后指针的乐观锁
        private final AtomicInteger refreshCount = new AtomicInteger(); // 刷新计数器，保证执行完所有刷新缓存操作再执行移除缓存操作

        public LruCacheNode(String key, T value) {
            this.key = key;
            this.value = value;
        }

        @Override
        @Nullable
        public T get() {
            return value;
        }

        @Nullable
        public String getKey() {
            return key;
        }

        public void increaseRefreshCount() {
            refreshCount.getAndIncrement();
        }

        /**
         * 将当前缓存节点从链表中断开
         * @param remove 是否是移除缓存导致的节点断开
         */
        private void unlink(boolean remove) {
            // 移除前等待刷新计数减为0
            if (remove) while (refreshCount.get() > 0) Thread.yield();

            boolean reentrant = false, shouldRelease = false, isSizeEven;
            LruCacheNode oldMid, newMid, oldPrev, oldNext;
            VarHandle lock;

            while (true) {
                // 获取中间节点锁，修改中间节点指针和节点状态
                while (!MIDPOINT.compareAndSet(ConcurrentLruPolicy.this, 0, 1)) Thread.yield();
                isSizeEven = (size.getAndDecrement() & 1) == 0;
                oldMid = midpoint;
                // 如果当前节点不是原中间节点，并且当前节点的状态为HOT并且移除前节点数为偶数，或当前节点的状态为COLD并且移除前节点数为奇数，不会改变中间节点，也不会影响任何节点的状态
                if (oldMid == this || (isSizeEven ^ this.state == 1)) {
                    // 如果移除前节点数为偶数，则原中间节点的前驱节点成为新中间节点
                    // 如果移除前节点数为奇数，则原中间节点的后继节点成为新中间节点
                    lock = isSizeEven ? PREV : NEXT;
                    while (!lock.compareAndSet(oldMid, 0, 1)) Thread.yield();
                    newMid = isSizeEven ? oldMid.prev : oldMid.next;
                    if (newMid == (isSizeEven ? head : null) || newMid == this) {
                        // 为了防止中间节点偏移到链表外，放弃并重试
                        lock.set(oldMid, 0);
                        MIDPOINT.set(ConcurrentLruPolicy.this, 0);
                        Thread.yield();
                        size.getAndIncrement();
                        continue;
                    }
                    midpoint = newMid;
                    lock.set(oldMid, 0);
                    // 如果当前节点不是原中间节点，并且当前节点的状态为COLD并且移除前节点数为偶数，则原中间节点的状态设置为COLD
                    // 如果当前节点不是原中间节点，并且当前节点的状态为HOT并且移除前节点数为奇数，则原中间节点的状态设置为HOT
                    if (oldMid != this) oldMid.state = isSizeEven ? 0 : 1;
                }
                break;
            }

            // 获取节点变动所需的指针锁
            while (!PREV.compareAndSet(this, 0, 1) || (shouldRelease = (oldPrev = this.prev) == null)) {
                // 如果前指针指向空，则释放1号锁，并进入下一轮循环进行重试，等待节点插入回链表，避免空指针异常
                if (shouldRelease) {
                    PREV.set(this, 0);
                    shouldRelease = false;
                }
                Thread.yield();
            }
            while (!NEXT.compareAndSet(oldPrev, 0, 1)) Thread.yield();
            while (!NEXT.compareAndSet(this, 0, 1) || (shouldRelease =
                    !PREV.compareAndSet((oldNext = this.next), 0, 1) && !(reentrant =
                            // 判断锁重入
                            (oldNext == tail && tailPrevLockReentrant == 1)))) {
                // 如果获取不到4号锁，则释放3号锁，并进入下一轮循环进行重试，避免死锁
                if (shouldRelease) {
                    NEXT.set(this, 0);
                    shouldRelease = false;
                }
                Thread.yield();
            }

            // 防止中间节点指针前倾，提前修改节点数据状态
            this.state = 1;

            // 释放中间节点锁
            MIDPOINT.set(ConcurrentLruPolicy.this, 0);

            // 将当前节点从链表断开
            oldNext.prev = oldPrev;
            oldPrev.next = oldNext;
            this.prev = null;
            this.next = null;

            // 释放节点指针锁
            if (reentrant) tailPrevLockReentrant = 0;
            PREV.set(oldNext, 0);
            NEXT.set(this, 0);
            NEXT.set(oldPrev, 0);
            PREV.set(this, 0);
        }

        /**
         * 将当前缓存节点插入到链表头部节点的位置
         */
        private void linkHead() {
            LruCacheNode headDummy = head, oldHead, oldMid;
            boolean shouldRelease = false;

            // 获取节点变动所需的指针锁
            while (!NEXT.compareAndSet(headDummy, 0, 1) || (shouldRelease =
                    !PREV.compareAndSet((oldHead = headDummy.next), 0, 1))) {
                // 如果获取不到2号锁，则释放1号锁，并进入下一轮循环进行重试，避免死锁
                if (shouldRelease) {
                    NEXT.set(headDummy, 0);
                    shouldRelease = false;
                }
                Thread.yield();
            }

            // 将当前节点插入链表头部节点位置
            this.prev = headDummy;
            headDummy.next = this;
            this.next = oldHead;
            oldHead.prev = this;

            // 释放节点指针锁
            PREV.set(oldHead, 0);
            NEXT.set(headDummy, 0);

            // 减少刷新计数
            refreshCount.getAndDecrement();

            while (true) {
                // 获取中间节点锁，修改中间节点指针和节点状态
                while (!MIDPOINT.compareAndSet(ConcurrentLruPolicy.this, 0, 1)) Thread.yield();
                // 如果插入前节点数为奇数，不会改变中间节点，也不会影响任何节点的状态
                // 如果插入前节点数为偶数，则原中间节点的状态设置为COLD，原中间节点的前驱节点成为新中间节点
                if ((size.getAndIncrement() & 1) == 0) {
                    oldMid = midpoint;
                    while (!PREV.compareAndSet(oldMid, 0, 1)) Thread.yield();
                    if (oldMid.prev == head) {
                        // 为了防止中间节点偏移到链表外，放弃并重试
                        PREV.set(oldMid, 0);
                        MIDPOINT.set(ConcurrentLruPolicy.this, 0);
                        Thread.yield();
                        size.getAndDecrement();
                        continue;
                    }
                    oldMid.state = 0;
                    midpoint = oldMid.prev;
                    PREV.set(oldMid, 0);
                }
                // 释放中间节点锁
                MIDPOINT.set(ConcurrentLruPolicy.this, 0);
                break;
            }
        }

        /**
         * 将当前缓存节点插入到链表中间节点的位置
         */
        private void linkMid() {
            LruCacheNode oldMid, oldMidPrev;
            // 获取中间节点锁
            while (!MIDPOINT.compareAndSet(ConcurrentLruPolicy.this, 0, 1)) Thread.yield();
            oldMid = midpoint;
            // 获取节点变动所需的指针锁
            while (!PREV.compareAndSet(oldMid, 0, 1)) Thread.yield();
            oldMidPrev = oldMid.prev;
            while (!NEXT.compareAndSet(oldMidPrev, 0, 1)) Thread.yield();

            this.prev = oldMidPrev;
            oldMidPrev.next = this;
            this.next = oldMid;
            oldMid.prev = this;

            // 修改中间节点指针和节点状态
            // 如果插入前节点数为奇数，不会改变中间节点，也不会影响任何节点的状态
            // 如果插入前节点数为偶数，则原中间节点的状态设置为COLD，当前节点成为新中间节点
            if ((size.getAndIncrement() & 1) == 0) {
                oldMid.state = 0;
                midpoint = this;
            }

            // 释放节点指针锁
            NEXT.set(oldMidPrev, 0);
            PREV.set(oldMid, 0);
            // 释放中间节点锁
            MIDPOINT.set(ConcurrentLruPolicy.this, 0);
        }

        /**
         * 新增缓存节点
         */
        private void insert() {
            linkMid();
        }

        /**
         * 移动缓存节点到链表头部
         */
        private void refresh() {
            unlink(false);
            linkHead();
        }

        /**
         * 移除尾部有效缓存节点
         */
        private void pushAway(Map<String, CacheWrapper<T>> map) {
            LruCacheNode tailDummy = tail, rm;
            boolean shouldRelease = false;

            // 获取尾部指示节点的前指针锁
            while (!PREV.compareAndSet(tailDummy, 0, 1) || (shouldRelease =
                    // 判断尾部节点是否为有效缓存项，如果是则移除，否则放弃重试
                    !map.remove((rm = tailDummy.prev).key, rm))) {
                if (shouldRelease) {
                    PREV.set(tailDummy, 0);
                    shouldRelease = false;
                }
                Thread.yield();
            }
            // 准备锁重入
            tailPrevLockReentrant = 1;
            rm.unlink(true);
        }

        /**
         * 移除缓存节点
         */
        private void remove() {
            unlink(true);
        }

        @Override
        public String toString() {
            return "{" + key + ", " + value + ", " + state + "}";
        }
    }

    /**
     * 弱一致性迭代器，仅保证从链表头部遍历到达链表尾部
     */
    class WeakIterator implements Iterator<CacheWrapper<T>> {
        private LruCacheNode current = head;

        @Override
        public boolean hasNext() {
            while (!NEXT.compareAndSet(current, 0, 1)) Thread.yield();
            releaseLockAfterNext();
            boolean hasNext = current.next != tail;
            if (!hasNext) releaseLockBeforeNext();
            return hasNext;
        }

        @Override
        public LruCacheNode next() {
            return current = current.next;
        }

        /**
         * 使用此迭代器时，需要外部进行try-finally处理，如果在调用next()方法前出现异常，调用此方法释放锁
         */
        public void releaseLockBeforeNext() {
            NEXT.set(current, 0);
        }

        /**
         * 使用此迭代器时，需要外部进行try-finally处理，如果在调用next()方法后出现异常，调用此方法释放锁
         */
        public void releaseLockAfterNext() {
            if (current != head) NEXT.set(current.prev, 0);
        }
    }

    /**
     * 查询缓存
     * @param node 缓存节点
     */
    @Override
    public T get(@Nullable CacheWrapper<T> node) {
        if (node != null) {
            // 缓存项刷新异步处理，并指定了调用者自己执行的拒绝策略
            refreshPool.submit(() -> ((LruCacheNode) node).refresh());
            return node.get();
        } else {
            return null;
        }
    }

    /**
     * 新增缓存
     * @param map 缓存存储的Map结构
     * @param value 缓存value
     * @return 缓存节点
     */
    @Override
    public LruCacheNode put(Map<String, CacheWrapper<T>> map, String key, T value) {
        LruCacheNode node = new LruCacheNode(key, value);
        // 如果缓存容量已满，则先淘汰链表尾部的有效缓存项再写入
        if (size.get() >= capacity) node.pushAway(map);
        node.insert();
        return node;
    }

    /**
     * 移除缓存
     * @param node 缓存节点
     */
    @Override
    public void remove(@Nullable CacheWrapper<T> node) {
        if (node != null) ((LruCacheNode) node).remove();
    }

    /**
     * 获取迭代器
     * @return 迭代器
     */
    @Override
    public Iterator<CacheWrapper<T>> iterator() {
        return new WeakIterator();
    }
}
