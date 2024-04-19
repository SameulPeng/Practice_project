package com.practice.policy.impl;

import com.practice.policy.CacheWrapper;
import com.practice.policy.EvictionPolicy;

import java.util.Map;

/**
 * 串行化LRU缓存淘汰策略控制器
 * @param <T> 缓存key的类型
 */
public class SerializedLruPolicy<T> implements EvictionPolicy<T> {
    private final LruSupport lruSupport; // 链表标志节点控制器
    private int size; // 缓存当前大小
    private final int capacity; // 缓存容量

    public SerializedLruPolicy(int capacity) {
        this.lruSupport = new LruSupport();
        this.capacity = capacity;
        this.size = 0;
    }

    class LruSupport {
        private final LruCacheNode head; // 链表头部节点的前驱节点，作为指示节点
        private final LruCacheNode tail; // 链表尾部节点的后继节点，作为指示节点
        private LruCacheNode midpoint; // 链表中间节点

        private LruSupport() {
            this.head = new LruCacheNode(null, null);
            this.tail = new LruCacheNode(null, null);
            this.head.next = this.tail;
            this.tail.prev = this.head;
            this.midpoint = this.tail;
        }

        private String getTailKey() {
            return tail.prev.key;
        }
    }

    class LruCacheNode implements CacheWrapper<T> {
        private LruCacheNode next; // 指向后继节点的指针
        private LruCacheNode prev; // 指向前驱节点的指针
        private State state; // 表示缓存节点的数据的冷热状态
        private final String key;  // 缓存key
        private final T value; // 缓存value

        public LruCacheNode(String key, T value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        /**
         * 枚举类
         * 表示缓存节点的数据的冷热状态，中间节点之前的节点的状态为HOT，中间节点之后的节点的状态为COLD，中间节点可能是两种状态之中的一种
         */
        enum State {
            HOT, COLD
        }

        /**
         * 枚举类
         * 表示缓存节点的行为，新增为INSERT，移动到链表头部为REFRESH，移除为REMOVE，新增并移除链表尾部节点为PUSH_AWAY
         */
        enum Act {
            INSERT, REFRESH, REMOVE, PUSH_AWAY
        }

        /**
         * 将当前缓存节点从链表中断开
         */
        private void unlink() {
            next.prev = this.prev;
            prev.next = this.next;
            this.prev = null;
            this.next = null;
        }

        /**
         * 将当前缓存节点插入到链表中指定节点的位置，指定节点向后移动
         * @param node 指定节点
         */
        private void link(LruCacheNode node) {
            this.prev = node.prev;
            this.next = node;
            node.prev.next = this;
            node.prev = this;
        }

        /**
         * 插入当前缓存节点后引起其他节点的状态变化
         * @param act 缓存节点的行为
         */
        private void alterState(Act act) {
            // 当前节点从链表移除
            if (act == Act.REMOVE) {
                // 如果当前节点不是原中间节点
                if (this != lruSupport.midpoint) {
                    // 当前节点的状态为HOT并且移除前节点数为偶数，或当前节点的状态为COLD并且移除前节点数为奇数，不会改变中间节点，也不会影响任何节点的状态
                    if (this.state == State.COLD ^ (size & 1) == 0) return;
                    // 如果移除前节点数为偶数，则原中间节点的状态设置为COLD
                    // 如果移除前节点数为奇数，则原中间节点的状态设置为HOT
                    lruSupport.midpoint.state = (size & 1) == 0 ? State.COLD : State.HOT;
                }
                // 如果移除前节点数为偶数，则原前驱节点成为新中间节点
                // 如果移除前节点数为奇数，则原后继节点成为新中间节点
                lruSupport.midpoint = (size & 1) == 0 ? lruSupport.midpoint.prev : lruSupport.midpoint.next;
            // 当前节点移动到链表头部节点位置
            } else if (act == Act.REFRESH) {
                // 如果当前节点不是原中间节点
                if (this != lruSupport.midpoint) {
                    // 如果当前节点状态为HOT，不会改变中间节点，也不会影响任何节点的状态
                    if (this.state == State.HOT) return;
                    // 原中间节点的状态设置为COLD
                    lruSupport.midpoint.state = State.COLD;
                }
                // 当前节点的状态设置为HOT，则原中间节点的前驱节点成为新中间节点
                this.state = State.HOT;
                lruSupport.midpoint = lruSupport.midpoint.prev;
            } else {
                // 当前节点的状态设置为HOT
                this.state = State.HOT;
                // 如果当前节点插入到链表，插入前节点数为奇数，不会改变中间节点，也不会影响任何节点的状态
                if (act == Act.INSERT && (size & 1) == 1) return;
                // 如果当前节点插入到链表，并且插入前节点数为偶数或移除链表尾部节点，则原中间节点的状态设置为COLD，当前节点成为新中间节点
                lruSupport.midpoint.state = State.COLD;
                lruSupport.midpoint = this;
            }
        }

        /**
         * 移动缓存节点到链表头部
         */
        private void refresh() {
            // 先改变状态再断开节点
            alterState(Act.REFRESH);
            unlink();
            link(lruSupport.head.next);
        }

        /**
         * 移除缓存节点
         */
        private void remove() {
            // 先改变状态再断开节点
            alterState(Act.REMOVE);
            unlink();
            size--;
        }

        /**
         * 新增缓存节点
         */
        private void insert() {
            // 先插入节点再改变状态
            link(lruSupport.midpoint);
            alterState(Act.INSERT);
            size++;
        }

        /**
         * 新增缓存节点并移除尾部节点
         */
        private void pushAway() {
            // 先断开节点再插入节点再改变状态
            lruSupport.tail.prev.unlink();
            link(lruSupport.midpoint);
            alterState(Act.PUSH_AWAY);
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    /**
     * 查询缓存
     * @param node 缓存节点
     */
    @Override
    public T get(CacheWrapper<T> node) {
        if (node != null) {
            ((LruCacheNode) node).refresh();
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
        // 如果缓存容量已满，则先淘汰链表尾部的缓存项再写入
        if (size >= capacity) {
            map.remove(lruSupport.getTailKey());
            node.pushAway();
        } else {
            node.insert();
        }
        return node;
    }

    /**
     * 移除缓存
     * @param node 缓存节点
     */
    @Override
    public void remove(CacheWrapper<T> node) {
        if (node != null) {
            ((LruCacheNode) node).remove();
        }
    }
}
