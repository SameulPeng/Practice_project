package com.practice.service;

import com.practice.cache.impl.ConcurrentLruLocalCache;
import com.practice.common.exception.BalanceNotEnoughException;
import com.practice.common.exception.IllegalAccountException;
import com.practice.common.result.RedPacketResult;
import com.practice.common.result.ShareResult;
import com.practice.config.RedPacketProperties;
import com.practice.dao.RedPacketDao;
import com.practice.extension.RedPacketExtensionComposite;
import com.practice.mapper.AccountInterface;
import com.practice.util.RedPacketKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@Profile("biz")
public class RedPacketService {
    @Autowired
    private RedPacketDao redPacketDao;
    @Autowired
    private AccountInterface accountInterface;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private RedPacketExtensionComposite extensionComposite; // 抢红包业务扩展组合类
    @Autowired
    private RedPacketProperties redPacketProperties; // 配置参数类
    private ConcurrentLruLocalCache<Map<String, String>> cache; // 本地缓存，存储红包key对应的抢红包结果
    private ConcurrentHashMap<String, AtomicInteger> atomicMap; // 原子整数Map，存储红包key对应的原子整数，用于避免抢红包阻塞
    private final AtomicInteger atomicCount = new AtomicInteger(); // 原子整数当前数量
    private final AtomicBoolean isAtomicMapFull = new AtomicBoolean(false); // 原子整数是否已满
    private final ConcurrentLinkedQueue<AtomicInteger> atomicPool = new ConcurrentLinkedQueue<>(); // 原子整数池，实现原子整数的复用
    private final ScheduledExecutorService scheduledPool = // 检查原子整数Map泄漏问题的巡逻线程的线程池
            Executors.newScheduledThreadPool(1, r -> new Thread(r, "KeyLeakPatroller"));
    private ExecutorService transactionPool; // 异步处理发起抢红包的多个网络通信操作的线程池

    @PostConstruct
    private void init() throws NoSuchFieldException, IllegalAccessException {
        // 初始化本地缓存
        this.cache = new ConcurrentLruLocalCache<>(redPacketProperties.getShare().getCacheSize());
        // 初始化原子整数Map
        this.atomicMap = new ConcurrentHashMap<>(redPacketProperties.getPublish().getAtomicMaxCount(), 1.0f);

        // 初始化线程池
        this.transactionPool = new ThreadPoolExecutor(
                redPacketProperties.getPublish().getMinThreads(),
                redPacketProperties.getPublish().getMaxThreads(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(redPacketProperties.getPublish().getQueueSize()),
                r -> new Thread(r, "TransactionHandler"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // 巡逻线程，定期检查原子整数Map中是否存在泄漏
        /*
            原子整数Map中的key会通过以下方式移除
                1.红包被抢完后，由第一个抢不到红包的用户移除
                2.红包key过期后，由Redis的key过期事件监听器回调移除
                3.红包结算时，由消息队列的消费者回调移除
            上述逻辑能够在绝大部分情况下移除key，但也可能全部没有执行成功，导致某个key在原子整数Map中长期存在并且无法访问
                1.红包没有被抢完
                2.没有启用Redis的事件监听机制
                3.由于某些原因，没有接收到消息队列的结算消息
            因此需要定期检查，避免泄漏
        */
        int interval = redPacketProperties.getPublish().getAtomicLeakPatrolInterval();
        scheduledPool.scheduleAtFixedRate(() -> {
            // 获取原子整数Map的快照
            Enumeration<String> keys = atomicMap.keys();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                // 计算红包的有效时限
                long limit = RedPacketKeyUtil.parseTimestamp(key)
                        + RedPacketKeyUtil.parseExpireTime(key) * 1000L;
                if (System.currentTimeMillis() > limit) removeFromAtomicMap(key);
            }
            // 以固定时间间隔，定期检查
        }, interval, interval, TimeUnit.MINUTES);
    }

    /**
     * 由Redis消息监听器在监听到红包key过期事件后或通过消息队列的消息进行红包结算时回调
     * @param key 红包key
     */
    public void removeFromAtomicMap(String key) {
        AtomicInteger i = atomicMap.remove(key);
        if (i != null) atomicPool.offer(i);
    }

    /**
     * 由Redis消息监听器在监听到红包结果key过期事件后回调
     * @param key 红包key
     */
    public void removeFromCache(String key) {
        cache.remove(key);
    }

    /**
     * 发起抢红包，根据大红包总金额和分派数量，预先分成若干小红包
     * @param key 红包key
     * @param userId 发起抢红包用户ID
     * @param amount 红包总金额，单位为分
     * @param shareNum 拆分小红包份数
     * @param expireTime 红包过期时长，单位为秒
     */
    public void publish(String key, String userId, int amount, int shareNum, int expireTime) {
        // 执行发起抢红包前的扩展方法
        extensionComposite.beforePublish(userId, amount, shareNum, expireTime);

        int tmp = amount;
        // 用于将大红包随机拆分成若干小红包，相比Random类，能够减少竞争
        ThreadLocalRandom random = ThreadLocalRandom.current();
        // 二倍均值算法
        String[] arr = new String[shareNum];
        int decr;
        for (int i = shareNum; i > 1; i--) {
            amount -= (decr = random.nextInt(1, (amount / i) << 1));
            arr[i - 1] = String.valueOf(decr);
        }
        arr[0] = String.valueOf(amount);

        // 使用线程池异步处理多个网络通信操作
        // 开启事务，当消息和Redis的key均创建成功后，提交账户操作
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                // 为了避免恶意攻击产生大量垃圾消息和key，先同步扣减账户余额
                try {
                    if (accountInterface.decreaseBalance(userId, tmp) != 1) throw new IllegalAccountException(userId);
                } catch (DataIntegrityViolationException e) {
                    throw new BalanceNotEnoughException(userId, tmp);
                }
                // 发送延时消息，用于结算
                FutureTask<Integer> messageFuture = new FutureTask<>(
                        () -> rocketMQTemplate
                                    .syncSendDelayTimeSeconds(
                                            // 将消息TAG设置为当前JVM编号，此消息将由当前JVM的消费者进行消费
                                            "RedPacketSettlement:" + redPacketProperties.getServiceId(),
                                            MessageBuilder.withPayload(key).build(), expireTime)
                                    .getSendStatus() == SendStatus.SEND_OK ? 1 : null
                );
                // 创建红包key
                FutureTask<Integer> keyFuture = new FutureTask<>(
                        () -> {
                            try {
                                redPacketDao.publish(key, arr, expireTime);
                                return 1;
                            } catch (Exception e) {
                                return null;
                            }
                        }
                );
                transactionPool.submit(messageFuture);
                transactionPool.submit(keyFuture);
                try {
                    if (messageFuture.get() == null) throw new RuntimeException("延时消息发送失败");
                    if (keyFuture.get() == null) throw new RuntimeException("红包key创建失败");
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        // 获取红包key对应的原子整数，初始值为红包份数
        AtomicInteger i = atomicPool.poll();
        if (i != null) {
            // 如果从原子整数池中获取到原子整数，则直接复用
            i.set(shareNum);
            atomicMap.put(key, i);
        } else if (!isAtomicMapFull.get()) {
            // 如果没有从原子整数池中获取到原子整数，但是原子整数数量未达到上限，则尝试创建新的原子整数
            if (atomicCount.getAndIncrement() < redPacketProperties.getPublish().getAtomicMaxCount()) {
                atomicMap.put(key, new AtomicInteger(shareNum));
            } else {
                isAtomicMapFull.set(true);
            }
        }

        // 执行发起抢红包后的扩展方法
        extensionComposite.afterPublish(userId, amount, shareNum, expireTime);
    }

    /**
     * 参与抢红包
     * @param key 红包key
     * @param userId 抢红包用户ID
     */
    public RedPacketResult<ShareResult> share(String key, String userId) {
        // 执行参与抢红包前的扩展方法
        extensionComposite.beforeShare(key, userId);

        Map<String, String> mapResult = null;
        ShareResult shareResult = null;
        // 设置最大重试次数，防止缓存穿透导致的死循环
        int tryTimes = 0;
        while (mapResult == null && shareResult == null && tryTimes++ <= redPacketProperties.getShare().getMaxTryTimes()) {
            // 如果在本地缓存中找不到对应的key，则准备访问Redis
            if ((mapResult = cache.get(key)) == null) {
                AtomicInteger count;
                int decr = 0;
                // 如果在原子整数Map中找不到对应的key，表示红包已经抢完或者系统中存在大量红包，可以通过竞争锁访问Redis回写本地缓存
                if ((count = atomicMap.get(key)) == null || (decr = count.decrementAndGet()) < 0) {
                    // 锁住key对应的字符串常量对象
                    synchronized (key.intern()) {
                        // 如果在本地缓存中仍找不到对应的key，则访问Redis
                        if ((mapResult = cache.get(key)) == null) {
                            shareResult = redPacketDao.share(key, userId);
                            // 如果返回结果为空，表明请求超时，正常释放锁，进入下一轮循环重试
                            // 如果抢不到红包，那么返回的是红包结果，写入本地缓存
                            if (shareResult != null && shareResult.getStatus() == 0) {
                                mapResult = shareResult.getResult();
                                // 移除预生成结果占位项
                                mapResult.remove(redPacketProperties.getBiz().getResultPlaceholder());
                                // 执行抢红包结果写入缓存前的扩展方法
                                cache.put(key, extensionComposite.onCache(mapResult));
                            }
                        }
                    }
                    // 由第一个抢不到红包的用户负责移除红包key对应的原子整数
                    if (count != null && decr == -1) removeFromAtomicMap(key);
                } else {
                    // 原子整数扣减到负数之前，都可以不必竞争锁，直接访问Redis
                    shareResult = redPacketDao.share(key, userId);
                    // 如果返回结果为空，表明请求超时，进入下一轮循环重试
                    // 如果抢不到红包，那么返回的是红包结果，写入本地缓存
                    if (shareResult != null && shareResult.getStatus() == 0) {
                        mapResult = shareResult.getResult();
                        // 移除预生成结果占位项
                        mapResult.remove(redPacketProperties.getBiz().getResultPlaceholder());
                        // 执行抢红包结果写入缓存前的扩展方法
                        cache.put(key, extensionComposite.onCache(mapResult));
                        // 如果原子整数还没扣减到负数，但是已经产生了红包结果，说明红包没有被抢完就过期，或通过竞争锁访问Redis的用户先抢完了红包，需要移除红包key对应的原子整数
                        removeFromAtomicMap(key);
                    }
                }
            }
        }

        // 对结果进行判断和进一步处理
        RedPacketResult<ShareResult> redPacketResult;
        if (mapResult == null && shareResult == null) {
            redPacketResult = RedPacketResult.error("没有找到红包，可能是网络异常或已经结束");
        } else {
            if (shareResult != null) {
                // 如果从Redis查询到结果
                if (shareResult.getStatus() == 0) {
                    // 如果标识为0，表示抢不到红包或红包结束后的结果查询，进一步判断用户是否抢到过红包，如果用户抢到过则将标识设置为1
                    // 如果红包结果为空集，表示红包结果key已经过期，直接返回空
                    if (shareResult.getResult().size() == 0) {
                        shareResult.setMsg("您查看的红包在较早的时候已经结束");
                        log.info("用户{}查询一个过早的红包结果", userId);
                    } else if (shareResult.getResult().get(userId) != null) {
                        shareResult.setStatus(1);
                        shareResult.setMsg("抢红包已经结束了，您抢到过红包");
                        log.info("用户{}抢到红包，查询结果", userId);
                    } else {
                        shareResult.setMsg("抢红包已经结束了，您没抢到红包");
                        log.info("用户{}没抢到红包，查询结果", userId);
                    }
                } else if (shareResult.getStatus() == 1) {
                    // 如果标识为1，表示抢到红包，此时抢红包仍未结束，直接返回，由前端将结果缓存到用户设备上
                    log.info("用户{}抢到红包，耗时{}秒", userId, shareResult.getTimeCost() / 1000f);
                } else {
                    // 如果标识为-1，表示已经参与过抢红包，由前端返回缓存在用户设备上的抢红包成功结果
                    log.info("用户{}重复参与抢红包", userId);
                }
            } else {
                // 如果从本地缓存查询到结果，进一步判断用户是否抢到过红包，如果用户抢到过则将标识设置为1
                if (mapResult.get(userId) != null) {
                    shareResult = ShareResult.shareSuccess("抢红包已经结束了，您抢到过红包", mapResult, 0L);
                    log.info("用户{}抢到红包，查询结果", userId);
                } else {
                    shareResult = ShareResult.shareFail("抢红包已经结束了，您没抢到红包", mapResult);
                    log.info("用户{}没抢到红包，查询结果", userId);
                }
            }
            redPacketResult = RedPacketResult.shareSuccess(shareResult);
        }

        // 执行参与抢红包后的扩展方法
        return extensionComposite.afterShare(key, userId, redPacketResult);
    }
}
