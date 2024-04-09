package com.practice.mq.consumer;

import com.practice.common.logging.ExtLogger;
import com.practice.common.pojo.BigDataInfo;
import com.practice.common.pojo.ShareInfo;
import com.practice.common.util.RedPacketKeyUtil;
import com.practice.config.RedPacketProperties;
import com.practice.extension.RedPacketExtensionComposite;
import com.practice.mapper.AccountInterface;
import com.practice.service.RedPacketService;
import org.apache.logging.log4j.Level;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.SelectorType;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ClassUtils;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 结算消息消费者
 */
@Component
@Profile("biz")
@RocketMQMessageListener(
        consumerGroup = "RedPacketMQConsumer",
        topic = "RedPacketSettlement",
        selectorType = SelectorType.TAG,
        // 将消息TAG过滤条件设置为当前JVM编号，消费由当前JVM发送的消息
        selectorExpression = "${red-packet.service-id}",
        // 设置消费超时时间为5分钟
        consumeTimeout = 5L
)
public class RedPacketMQConsumer implements RocketMQListener<String> {
    private static final ExtLogger log = ExtLogger.create(RedPacketMQConsumer.class); // 日志Logger对象
    @Autowired
    @SuppressWarnings("rawtypes")
    private RedisTemplate redisTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private RedissonClient redisson;
    @Autowired
    private RedPacketService redPacketService;
    @Autowired
    private RedPacketExtensionComposite extensionComposite; // 抢红包业务扩展组合类
    @Autowired
    private AccountInterface accountInterface; // 模拟账户业务接口类
    @Autowired
    private RedPacketProperties redPacketProperties; // 配置参数类
    private String settleScript; // Redis红包结果key设置过期时间Lua脚本

    @PostConstruct
    private void init() {
        // 初始化Redis红包结果key过期时间设置Lua脚本
        try (BufferedReader br = new BufferedReader(new FileReader(
                ClassUtils.getDefaultClassLoader().getResource("").getPath() + "lua/settle.lua"))) {
            char[] chars = new char[512];
            int len;
            StringBuilder sb = new StringBuilder();
            while ((len = br.read(chars)) != -1) {
                sb.append(chars, 0, len);
            }
            settleScript = sb.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 抢红包结算<br/>
     * RocketMQ底层默认实现了多线程消费，默认线程数为20
     * @param key 红包key
     */
    @Override
    @SuppressWarnings("unchecked")
    public void onMessage(String key) {
        String publisherId = RedPacketKeyUtil.parseUserId(key);
        String resultKey = redPacketProperties.getBiz().getResultPrefix() + key;
        Map<String, String> mapResult = null;
        int amount = RedPacketKeyUtil.parseAmount(key);
        long timestamp = System.currentTimeMillis();

        // 使用分布式锁Redisson，保证幂等性处理的并发安全
        /*
            由于幂等性通过红包结果key的过期时间来保证，但获取和设置过期时间的两步操作本身不具有原子性，因此需要通过分布式锁实现原子性
            虽然通过消息TAG的机制，尽量保证了同一个JVM实例发送的消息由自身进行消费，但是此处使用分布式锁而非JVM锁是出于以下考虑
                JVM编号仅用于构造全局唯一的红包key，本身既无业务含义，也是自主设置而非通过注册中心进行唯一分配的
                因此，此项目模块的JVM可以不依赖注册中心运行，因此也对其他JVM无感知，从机制上允许了两个不同JVM实例拥有编号
                虽然JVM编号应当唯一，但是如果由于配置出错导致JVM编号重复，在运行时可能出现两个不同JVM实例上的消费者订阅相同消息TAG的情况，而这种逻辑上的错误不会影响运行
                上述情况发生时，重复消息的幂等性就无法由JVM锁保证，因此使用分布式锁，对于涉及金额的幂等性处理，采用偏保守的策略
            此外，在最后设置红包结果key的过期时间时，再次检查过期时间，并使用Lua脚本实现了这两步操作的原子性，使结算过程实现了类似CAS的操作，保证幂等性
            如果不使用分布式锁，也能保证幂等性，但是由于设置红包结果key在数据库访问之后执行，不使用分布式锁会导致重复消息增加数据库的访问压力，因此还是使用分布式锁
        */
        RLock lock = redisson.getLock(key);
        // 由于设置了消费超时时间为5分钟，超过5分钟则认为消费失败且事务不会被提交，因此锁的持有时间设置为5分钟
        lock.lock(300, TimeUnit.SECONDS);
        try {
            // 获取红包结果key的过期时间
            Long expire = redisTemplate.getExpire(resultKey);
            // 如果红包结果key的过期时间不是-1，表示已经被设置过期时间或已经过期，即结算处理已经完成，当前消息是重复消息，应当忽略
            if (expire != null && expire == -1) {
                mapResult = redisTemplate.opsForHash().entries(resultKey);
                // 整理红包结果
                Map<String, Integer> result = settle(mapResult, amount, publisherId);
                log.biz("[{}] [ ] 红包结算结果 {} ", key, result);
                // 开启事务，当Redis中的key成功设置过期时间后，提交账户操作
                transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                    @Override
                    protected void doInTransactionWithoutResult(TransactionStatus status) {
                        // 调用金融账户模块API进行结算
                        accountInterface.batchIncreaseBalance(result);
                        // 通过为红包结果key设置过期时间，保证结算处理的幂等性
                        // 再次检查红包结果key的过期时间是否为-1，如果是则设置过期时间，通过Lua脚本实现两步操作的原子性
                        Long success = (Long) redisTemplate.execute(new DefaultRedisScript<>(settleScript, Long.class),
                                Arrays.asList(resultKey), String.valueOf(redPacketProperties.getBiz().getResultKeepTime()));
                        if (success == null) {
                            throw new RuntimeException("[" + key + "] 访问Redis异常，结算失败");
                        }
                        if (success != 1) {
                            throw new RuntimeException("[" + key + "] 重复结算，结算失败");
                        }
                    }
                });
                log.biz("[{}] [ ] 红包结算完成", key);

                // 执行红包结算后具有幂等性的扩展方法
                extensionComposite.afterSettlementIdempotent(key);
            }
        } finally {
            lock.unlock();
        }

        if (mapResult != null && log.isEnabled(Level.getLevel("BIGDATA"))) {
            Map<String, ShareInfo> map = fullSettle(mapResult);
            log.bigdata("{}", BigDataInfo.of(
                    BigDataInfo.Status.SETTLE, key, null, null, null,
                    BigDataInfo.Settle.of(map, amount, timestamp), null
                    ).encode()
            );
        }

        // 执行红包结算后的扩展方法
        extensionComposite.afterSettlement(key);

        // 将key从原子整数Map中移除
        redPacketService.removeFromAtomicMap(key);
    }

    /**
     * 整理红包结果
     * @param mapResult 从Redis获取的红包结果原始信息
     * @param amount 红包总金额
     * @param publisherId 发起用户ID
     * @return 整理后的红包结果
     */
    private Map<String, Integer> settle(Map<String, String> mapResult, int amount, String publisherId) {
        HashMap<String, Integer> result = new HashMap<>();

        // 移除预生成结果占位项
        mapResult.remove(redPacketProperties.getBiz().getResultPlaceholder());
        // 如果红包的发起者也参与了抢红包，则先移除，便于整理
        mapResult.remove(publisherId);
        // 遍历红包结果
        for (Map.Entry<String, String> entry : mapResult.entrySet()) {
            // 从每项结果提取金额
            String value = entry.getValue();
            int share = Integer.parseInt(value.substring(0, value.indexOf('-')));
            // 为每个抢到红包的用户增加账户余额
            result.put(entry.getKey(), share);
            amount -= share;
        }
        // 没有被抢完的红包金额以及本人抢到的红包金额，退回到发起者的账户
        if (amount > 0) result.put(publisherId, amount);

        return result;
    }

    /**
     * 解析红包结果原始信息
     * @param mapResult 从Redis获取的红包结果原始信息
     * @return 解析后的红包结果
     */
    private Map<String, ShareInfo> fullSettle(Map<String, String> mapResult) {
        HashMap<String, ShareInfo> result = new HashMap<>();

        // 移除预生成结果占位项
        mapResult.remove(redPacketProperties.getBiz().getResultPlaceholder());
        // 遍历红包结果
        for (Map.Entry<String, String> entry : mapResult.entrySet()) {
            // 从每项结果提取耗时和金额
            String value = entry.getValue();
            int idx = value.indexOf('-');
            ShareInfo info = new ShareInfo(Integer.parseInt(value.substring(0, idx)),
                    RedPacketKeyUtil.decodeTimeCost(value.substring(idx + 1)));
            result.put(entry.getKey(), info);
        }

        return result;
    }
}
