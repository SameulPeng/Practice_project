package com.practice.mq.consumer;

import com.practice.config.RedPacketProperties;
import com.practice.mapper.AccountInterface;
import com.practice.util.RedPacketKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 结算消息消费者
 */
@Slf4j
@Component
@Profile("biz")
@RocketMQMessageListener(consumerGroup = "RedPacketMQConsumer", topic = "RedPacketSettlement")
public class RedPacketMQConsumer implements RocketMQListener<String> {
    @Autowired
    @SuppressWarnings("rawtypes")
    private RedisTemplate redisTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private AccountInterface accountInterface; // 模拟账户业务接口类
    @Autowired
    private RedPacketProperties redPacketProperties; // 配置参数类

    /**
     * 抢红包结算
     * @param key 红包key
     */
    @Override
    @SuppressWarnings("unchecked")
    public void onMessage(String key) {
        String publisherId = RedPacketKeyUtil.parseUserId(key);
        String resultKey = redPacketProperties.getBiz().getResultPrefix() + key;

        // todo 原子性
        Long expire = redisTemplate.getExpire(resultKey);
        // 如果红包结果key的过期时间不是-1，表示已经被设置过期时间或已经过期，即结算处理已经完成，当前消息是重复消息，应当忽略
        if (expire != null && expire == -1) {
            Map<String, String> mapResult = redisTemplate.opsForHash().entries(resultKey);
            // 整理抢红包结果
            int amount = RedPacketKeyUtil.parseAmount(key);
            Map<String, Integer> result = settle(mapResult, amount, publisherId);
            // 开启事务，当Redis中的key成功设置过期时间后，提交账户操作
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    // 调用金融账户模块API进行结算
                    accountInterface.batchIncreaseBalance(result);
                    // 通过为红包结果key设置过期时间，保证结算处理的幂等性
                    redisTemplate.expire(resultKey, redPacketProperties.getBiz().getResultKeepTime(), TimeUnit.SECONDS);
                }
            });
            log.info("红包{}结算完成", key);
        }
    }

    private Map<String, Integer> settle(Map<String, String> mapResult, int amount, String publisherId) {
        HashMap<String, Integer> result = new HashMap<>();

        // 移除预生成结果占位项
        mapResult.remove(redPacketProperties.getBiz().getResultPlaceholder());
        // 如果红包的发起者也参与了抢红包，则先移除，便于整理
        mapResult.remove(publisherId);
        // 遍历红包结果
        for (Map.Entry<String, String> entry :mapResult.entrySet()) {
            int share = Integer.parseInt(entry.getValue());
            amount -= share;
            // 为每个抢到红包的用户增加账户余额
            result.put(entry.getKey(), share);
        }
        // 没有被抢完的红包金额以及本人抢到的红包金额，退回到发起者的账户
        if (amount > 0) result.put(publisherId, amount);

        log.info("结算结果：{}", result);
        return result;
    }
}
