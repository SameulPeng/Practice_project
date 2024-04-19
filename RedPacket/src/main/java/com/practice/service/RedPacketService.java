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
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Profile({"biz-dev", "biz-test" ,"biz-prod"})
public class RedPacketService {
    private final ExtLogger log = ExtLogger.create(RedPacketService.class); // 日志Logger对象
    private RedPacketDao redPacketDao;
    private AccountInterface accountInterface;
    private RocketMQTemplate rocketMQTemplate;
    private TransactionTemplate transactionTemplate;
    private RedPacketExtensionComposite extensionComposite; // 抢红包业务扩展组合类
    private RedPacketProperties redPacketProperties; // 配置参数类
    private ConcurrentLruLocalCache<Map<String, Object>> cache; // 本地缓存，存储红包key对应的抢红包结果
    private AtomicMap atomicMap; // 原子整数Map，存储红包key对应的原子整数，用于避免抢红包阻塞
    private ExecutorService transactionPool; // 异步处理发起抢红包的多个网络通信操作的线程池

    @Autowired
    private void setRedPacketDao(RedPacketDao redPacketDao) {
        this.redPacketDao = redPacketDao;
    }

    @Autowired
    private void setAccountInterface(AccountInterface accountInterface) {
        this.accountInterface = accountInterface;
    }

    @Autowired
    private void setRocketMQTemplate(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Autowired
    private void setTransactionTemplate(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Autowired
    private void setExtensionComposite(RedPacketExtensionComposite extensionComposite) {
        this.extensionComposite = extensionComposite;
    }

    @Autowired
    private void setRedPacketProperties(RedPacketProperties redPacketProperties) {
        this.redPacketProperties = redPacketProperties;
    }

    @PostConstruct
    private void init() throws NoSuchFieldException, IllegalAccessException {
        // 初始化本地缓存
        this.cache = new ConcurrentLruLocalCache<>(redPacketProperties.getShare().getCacheSize());
        // 初始化原子整数Map
        this.atomicMap = new AtomicMap(
                redPacketProperties.getPublish().getAtomicKeepTime(),
                redPacketProperties.getPublish().getAtomicMapSize()
        );
        // 初始化线程池
        this.transactionPool = new ThreadPoolExecutor(
                redPacketProperties.getPublish().getMinThreads(),
                redPacketProperties.getPublish().getMaxThreads(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(redPacketProperties.getPublish().getQueueSize()),
                r -> new Thread(r, "TransactionHandler"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
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
                // 在Redis中创建红包key
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
                // 发送延时消息，用于结算
                FutureTask<Integer> messageFuture = new FutureTask<>(
                        () -> rocketMQTemplate
                                    .syncSendDelayTimeSeconds("RedPacketSettlement",
                                            MessageBuilder.withPayload(key).build(), expireTime)
                                    .getSendStatus() == SendStatus.SEND_OK ? 1 : null
                );
                transactionPool.submit(keyFuture);
                transactionPool.submit(messageFuture);
                try {
                    if (keyFuture.get() == null) throw new RuntimeException("红包key创建失败");
                    if (messageFuture.get() == null) {
                        // 如果消息发送失败，则预生成的红包结果key会无法访问，造成泄漏，因此需要主动移除
                        redPacketDao.removeResult(key);
                        throw new RuntimeException("延时消息发送失败");
                    }
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        // 创建红包key对应的原子整数，初始值为红包份数
        atomicMap.put(key, shareNum);

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
        while (mapResult == null && shareResult == null
                && tryTimes++ <= redPacketProperties.getShare().getMaxTryTimes()) {
            // 如果在本地缓存中找不到对应的key，则准备访问Redis
            if ((mapResult = cache.get(key)) == null) {
                AtomicInteger count;
                // 如果在原子整数Map中找不到对应的key，表示红包已经抢完或者系统中存在大量红包，可以通过竞争锁访问Redis回写本地缓存
                if ((count = atomicMap.get(key)) == null || count.decrementAndGet() < 0) {
                    // 锁住key对应的字符串常量对象
                    synchronized (key.intern()) {
                        // 如果在本地缓存中仍找不到对应的key，则访问Redis
                        if ((mapResult = cache.get(key)) == null) {
                            shareResult = redPacketDao.share(key, userId);
                            // 如果返回结果为空，表明请求超时，正常释放锁，进入下一轮循环重试
                            // 如果抢不到红包，那么返回的是红包结果，写入本地缓存
                            if (shareResult != null && shareResult.getStatus() == 0) mapResult = doCache(key, shareResult);
                        }
                    }
                } else {
                    // 原子整数扣减到负数之前，都可以不必竞争锁，直接访问Redis
                    shareResult = redPacketDao.share(key, userId);
                    // 如果返回结果为空，表明请求超时，进入下一轮循环重试
                    // 如果抢不到红包，那么返回的是红包结果，写入本地缓存
                    if (shareResult != null && shareResult.getStatus() == 0) mapResult = doCache(key, shareResult);
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
        // 如果红包结果为空集，表示红包结果key已经过期或无效，直接返回空，统一视作过期处理
        if (mapResult.size() == 0) return null;
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
                        shareResult = ShareResult.share(ShareResult.ShareType.FAIL_NOT_FOUND);
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
                    log.biz("[{}] [用户 {}] 抢到了红包，金额 {} 元，耗时 {} 秒", key, userId, share / 100f, timeCost / 1000f);
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
                    shareResult = ShareResult.share(ShareResult.ShareType.FAIL_END, mapResult);
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

    /**
     * 原子整数Map，存储红包key对应的原子整数，用于避免抢红包阻塞<br/>
     * 内部使用两个Map定期交换的方式实现主动清理<br/>
     * 原子整数的保留时长，最短为一倍交换间隔，最长为两倍交换间隔
     */
    private static class AtomicMap {
        /**
         * 0号Map
         */
        private final ConcurrentHashMap<String, AtomicInteger> map0;
        /**
         * 1号Map
         */
        private final ConcurrentHashMap<String, AtomicInteger> map1;
        /**
         * 当前Map
         */
        private ConcurrentHashMap<String, AtomicInteger> current;
        /**
         * 非当前Map
         */
        private ConcurrentHashMap<String, AtomicInteger> last;
        /**
         * 当前Map原子整数数量
         */
        private final AtomicInteger count;
        /**
         * 单个Map原子整数数量上限
         */
        private final int capacity;

        private AtomicMap(int swapInterval, int capacity) {
            // 单个Map的容量为原子整数数量上限的一半
            int capacity0 = capacity >>> 1;
            this.map0 = new ConcurrentHashMap<>(capacity0, 1.0f);
            this.map1 = new ConcurrentHashMap<>(capacity0, 1.0f);
            this.current = map0;
            this.last = map1;
            this.count = new AtomicInteger(0);
            this.capacity = capacity0;

            // 交换线程，根据交换间隔定期清理原子整数
            Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
                // 清空非当前Map
                last.clear();
                // 交换当前Map和非当前Map，并将当前Map原子整数数量设置为0
                ConcurrentHashMap<String, AtomicInteger> map = current;
                current = last;
                count.set(0);
                last = map;
            }, swapInterval, swapInterval, TimeUnit.SECONDS);
        }

        /**
         * 尝试创建红包key对应的原子整数并放入原子整数Map
         * @param key 红包key
         * @param shareNum 红包份数
         */
        private void put(String key, int shareNum) {
            // 如果未达到原子整数上限，则为红包key创建对应的原子整数并放入当前Map
            // 不使用getAndIncrement()方法，防止溢出
            if (count.getAndUpdate(i -> i < capacity ? ++i : capacity) < capacity) {
                current.put(key, new AtomicInteger(shareNum));
            }
        }

        /**
         * 尝试获取红包key对应的原子整数
         * @param key 红包key
         * @return 红包key对应的原子整数
         */
        private AtomicInteger get(String key) {
            // 在两个Map中查找红包key对应的原子整数
            AtomicInteger result= map0.get(key);
            return result != null ? result : map1.get(key);
        }
    }
}
