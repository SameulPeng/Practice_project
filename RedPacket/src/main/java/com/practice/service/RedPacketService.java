package com.practice.service;

import com.practice.cache.impl.ConcurrentLruLocalCache;
import com.practice.common.exception.BalanceNotEnoughException;
import com.practice.common.exception.IllegalAccountException;
import com.practice.common.logging.ExtLogger;
import com.practice.common.pojo.BigDataInfo;
import com.practice.common.pojo.ShareInfo;
import com.practice.common.result.RedPacketResult;
import com.practice.common.result.ShareResult;
import com.practice.common.util.RedPacketKeyUtil;
import com.practice.config.RedPacketProperties;
import com.practice.dao.RedPacketDao;
import com.practice.extension.RedPacketExtensionComposite;
import com.practice.mapper.AccountInterface;
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

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Profile("biz")
public class RedPacketService {
    private final ExtLogger log = ExtLogger.create(RedPacketService.class); // 日志Logger对象
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
    private ConcurrentLruLocalCache<Map<String, Object>> cache; // 本地缓存，存储红包key对应的抢红包结果
    private ConcurrentHashMap<String, AtomicInteger> atomicMap; // 原子整数Map，存储红包key对应的原子整数，用于避免抢红包阻塞
    private final AtomicInteger atomicCount = new AtomicInteger(); // 原子整数当前数量
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
        if (i != null) atomicCount.decrementAndGet();
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
     *
     * @param key        红包key
     * @param userId     发起抢红包用户ID
     * @param amount     红包总金额，单位为分
     * @param shareNum   拆分小红包份数
     * @param expireTime 红包过期时长，单位为秒
     * @param timestamp 红包发起毫秒时间戳
     */
    public void publish(String key, String userId, int amount, int shareNum, int expireTime, long timestamp) {
        // 执行发起抢红包前的扩展方法
        extensionComposite.beforePublish(userId, amount, shareNum, expireTime);

        // 将大红包预先分割成若干小红包
        String[] arr = splitRedPacket(amount, shareNum);

        // 使用线程池异步处理多个网络通信操作
        // 开启事务，当消息和Redis的key均创建成功后，提交账户操作
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                // 为了避免恶意攻击产生大量垃圾消息和key，先同步扣减账户余额
                try {
                    if (accountInterface.decreaseBalance(userId, amount) != 1) throw new IllegalAccountException(userId);
                } catch (DataIntegrityViolationException e) {
                    throw new BalanceNotEnoughException(userId, amount);
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

        // 如果没有从原子整数池中获取到原子整数，但是原子整数数量未达到上限，则尝试创建新的原子整数
        // 没有使用getAndUpdate()方法，是因为竞争通常较为激烈，可能会导致多次执行原子整数Map的put操作，虽然具有幂等性，但是较为浪费资源
        if (atomicCount.getAndIncrement() < redPacketProperties.getPublish().getAtomicMaxCount()) {
            // 创建红包key对应的原子整数，初始值为红包份数
            atomicMap.put(key, new AtomicInteger(shareNum));
        } else {
            atomicCount.getAndDecrement();
        }

        log.biz("[{}] [ ] 红包创建成功，有效期 {} 秒", key, expireTime);
        // 使用惰性日志
        log.bigdata("{}", () -> BigDataInfo.of(
                BigDataInfo.Status.PUBLISH, key, userId,
                BigDataInfo.Publish.of(amount, shareNum, expireTime, timestamp),
                null, null, null
                ).encode()
        );

        // 执行发起抢红包后的扩展方法
        extensionComposite.afterPublish(key, userId, amount, shareNum, expireTime);
    }

    /**
     * 参与抢红包
     * @param key 红包key
     * @param userId 抢红包用户ID
     */
    @SuppressWarnings("rawtypes")
    public RedPacketResult share(String key, String userId) {
        // 执行参与抢红包前的扩展方法
        extensionComposite.beforeShare(key, userId);

        Map<String, Object> mapResult = null;
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
                            if (shareResult != null && shareResult.getStatus() == 0) {
                                // 如果抢不到红包，那么返回的是红包结果，写入本地缓存
                                mapResult = doCache(key, shareResult);
                            }
                        }
                    }
                    // 由第一个抢不到红包的用户负责移除红包key对应的原子整数
                    if (count != null && decr == -1) removeFromAtomicMap(key);
                } else {
                    // 原子整数扣减到负数之前，都可以不必竞争锁，直接访问Redis
                    shareResult = redPacketDao.share(key, userId);
                    // 如果返回结果为空，表明请求超时，进入下一轮循环重试
                    if (shareResult != null && shareResult.getStatus() == 0) {
                        // 如果抢不到红包，那么返回的是红包结果，写入本地缓存
                        mapResult = doCache(key, shareResult);
                        // 如果原子整数还没扣减到负数，但是已经产生了红包结果，说明红包没有被抢完就过期，或通过竞争锁访问Redis的用户先抢完了红包，需要移除红包key对应的原子整数
                        removeFromAtomicMap(key);
                    }
                }
            }
        }

        // 对结果进行判断和进一步处理
        RedPacketResult redPacketResult = doRedPacketResult(mapResult, shareResult, userId, key);

        // 执行参与抢红包后的扩展方法
        return extensionComposite.afterShare(key, userId, redPacketResult);
    }

    /**
     * 将红包总金额分割成若干小金额
     * @param amount 红包总金额
     * @param shareNum 拆分小红包份数
     * @return 小红包金额数组
     */
    private String[] splitRedPacket(int amount, int shareNum) {
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

        return arr;
    }

    /**
     * 写入缓存前将红包结果中的原始信息进行解析
     * @param mapResult 从Redis获取的红包结果原始信息
     */
    private void parseMapResult(Map<String, Object> mapResult) {
        // 将红包结果中的每个用户抢到的红包金额和耗时进行解析封装
        for (Map.Entry<String, Object> entry : mapResult.entrySet()) {
            String shareAndTimeCost = (String) entry.getValue();
            mapResult.put(entry.getKey(), parseMapEntry(shareAndTimeCost));
        }
    }

    /**
     * 对红包结果中的原始信息项进行解析并封装
     * @param value 原始信息项
     * @return 信息封装类，包含每个用户抢到的红包金额和耗时
     */
    private ShareInfo parseMapEntry(String value) {
        int idx = value.indexOf('-');
        return new ShareInfo(Integer.parseInt(value.substring(0, idx)),
                RedPacketKeyUtil.decodeTimeCost(value.substring(idx + 1)));
    }

    /**
     * 从抢红包结果中获取红包结果，进行前置处理后写入缓存
     * @param key 红包key
     * @param shareResult 抢红包结果
     */
    @Nullable
    private Map<String, Object> doCache(String key, ShareResult shareResult) {
        Map<String, Object> mapResult = shareResult.getMapResult();
        // 如果红包结果为空集，表示红包结果key已经过期，直接返回空
        if (mapResult.size() == 0) {
            return null;
        }
        // 移除预生成结果占位项
        mapResult.remove(redPacketProperties.getBiz().getResultPlaceholder());
        // 解析红包结果原始信息
        parseMapResult(mapResult);
        // 执行抢红包结果写入缓存前的扩展方法
        cache.put(key, extensionComposite.onCache(mapResult));
        return mapResult;
    }


    /**
     * 处理参与抢红包结果
     * @param mapResult 红包结果
     * @param shareResult 抢红包结果
     * @param userId 用户ID
     * @param key 红包key
     * @return 参与抢红包结果
     */
    @SuppressWarnings("rawtypes")
    private RedPacketResult doRedPacketResult(Map<String, Object> mapResult, ShareResult shareResult, String userId, String key) {
        long timestamp = System.currentTimeMillis();
        RedPacketResult redPacketResult;
        boolean checkShareSuccess = false;
        if (mapResult == null && shareResult == null) {
            redPacketResult = RedPacketResult.error(RedPacketResult.ErrorType.SHARE_ERROR);
        } else {
            if (shareResult != null) {
                // 如果从Redis查询到结果
                int status = shareResult.getStatus();
                if (status == 0) {
                    // 如果标识为0，表示抢不到红包或红包结束后的结果查询，进一步判断用户是否抢到过红包
                    if (mapResult == null) {
                        // 如果红包结果为空，表示红包结果key已经过期
                        shareResult = ShareResult.share(ShareResult.ShareType.FAIL_NOT_FOUND, null, 0, 0L);
                        log.biz("[{}] [用户 {}] 查询一个过早的红包结果", key, userId);
                        // 使用惰性日志
                        log.bigdata("{}", () -> BigDataInfo.of(
                                BigDataInfo.Status.SHARE, key, userId, null,
                                BigDataInfo.Share.of(BigDataInfo.Share.ShareType.FAIL_NOT_FOUND, 0, 0L, timestamp),
                                null, null
                                ).encode()
                        );
                    } else {
                        checkShareSuccess = true;
                    }
                } else if (status == 1) {
                    // 如果标识为1，表示抢到红包，此时抢红包仍未结束，直接返回
                    int share = shareResult.getShare();
                    long timeCost = shareResult.getTimeCost();
                    log.biz("[{}] [用户 {}] 抢到了红包，金额 {} 元，耗时 {} 秒", key, userId, share, timeCost / 1000f);
                    // 使用惰性日志
                    log.bigdata("{}", () -> BigDataInfo.of(
                            BigDataInfo.Status.SHARE, key, userId, null,
                            BigDataInfo.Share.of(BigDataInfo.Share.ShareType.SUCCESS_ONGOING, share, timeCost, timestamp),
                            null, null
                            ).encode()
                    );
                } else {
                    // 如果标识为-1，表示已经参与过抢红包
                    int share = shareResult.getShare();
                    long timeCost = shareResult.getTimeCost();
                    log.biz("[{}] [用户 {}] 重复参与抢红包", key, userId);
                    // 使用惰性日志
                    log.bigdata("{}", () -> BigDataInfo.of(
                            BigDataInfo.Status.SHARE, key, userId, null,
                            BigDataInfo.Share.of(BigDataInfo.Share.ShareType.FAIL_REDO, share, timeCost, timestamp),
                            null, null
                            ).encode()
                    );
                }
            } else {
                // 如果从本地缓存查询到结果，进一步判断用户是否抢到过红包
                checkShareSuccess = true;
            }
            if (checkShareSuccess) {
                ShareInfo info = (ShareInfo) mapResult.get(userId);
                if (info != null) {
                    int share = info.getShare();
                    long timeCost = info.getTimeCost();
                    shareResult = ShareResult.share(ShareResult.ShareType.SUCCESS_END, mapResult,
                            share, timeCost);
                    log.biz("[{}] [用户 {}] 抢到过红包，查询结果", key, userId);
                    // 使用惰性日志
                    log.bigdata("{}", () -> BigDataInfo.of(
                            BigDataInfo.Status.SHARE, key, userId, null,
                            BigDataInfo.Share.of(BigDataInfo.Share.ShareType.SUCCESS_END, share, timeCost, timestamp),
                            null, null
                            ).encode()
                    );
                } else {
                    shareResult = ShareResult.share(ShareResult.ShareType.FAIL_END, mapResult, 0, 0L);
                    log.biz("[{}] [用户 {}] 没抢到红包，查询结果", key, userId);
                    // 使用惰性日志
                    log.bigdata("{}", () -> BigDataInfo.of(
                            BigDataInfo.Status.SHARE, key, userId, null,
                            BigDataInfo.Share.of(BigDataInfo.Share.ShareType.FAIL_END, 0, 0L, timestamp),
                            null, null
                            ).encode()
                    );
                }
            }
            redPacketResult = RedPacketResult.shareSuccess(shareResult);
        }
        return redPacketResult;
    }
}
