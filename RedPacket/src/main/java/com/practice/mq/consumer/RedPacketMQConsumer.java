package com.practice.mq.consumer;

import com.practice.mapper.AccountMapper;
import com.practice.util.RedPacketKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 结算消息消费者
 */
@Slf4j
@Component
@RocketMQMessageListener(consumerGroup = "RedPacketMQConsumer", topic = "RedPacketSettlement")
public class RedPacketMQConsumer implements RocketMQListener<String> {
    @Autowired
    @SuppressWarnings("rawtypes")
    private RedisTemplate redisTemplate;
    @Autowired
    private AccountMapper accountMapper;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Value("${red-packet.key.result-prefix}")
    private String RESULT_KEY_PREFIX; // Redis中的红包结果key的业务前缀
    @Value("${red-packet.key.result-placeholder}")
    private String RESULT_KEY_PLACEHOLDER; // 预生成红包结果key所使用的占位项

    /**
     * 抢红包结算
     * @param key 红包key
     */
    @Override
    @SuppressWarnings("unchecked")
    public void onMessage(String key) {
        String userIdAndAmount = RedPacketKeyUtil.getUserIdAndAmount(key);
        String publisherId = RedPacketKeyUtil.parseUserId(userIdAndAmount);
        String resultKey = RESULT_KEY_PREFIX + key;

        // todo 原子性
        Long expire = redisTemplate.getExpire(resultKey);
        // 如果红包结果key的过期时间不是-1，表示已经被设置过期时间或已经过期，即结算处理已经完成，当前消息是重复消息，应当忽略
        if (expire != null && expire == -1) {
            Map<String, String> mapResult = redisTemplate.opsForHash().entries(resultKey);
            // 移除预生成结果占位项
            mapResult.remove(RESULT_KEY_PLACEHOLDER);
            // 发起者也可以参与抢红包，因此移除对应项，减少同一用户的数据库操作次数
            mapResult.remove(publisherId);
            // 开启事务，当Redis中的key成功设置过期时间后，提交账户操作
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    int amount = RedPacketKeyUtil.parseAmount(userIdAndAmount);
                    // 遍历红包结果，为每个抢到红包的用户增加账户余额
                    for (Map.Entry<String, String> entry : mapResult.entrySet()) {
                        int share = Integer.parseInt(entry.getValue());
                        amount -= share;
                        accountMapper.increaseBalance(entry.getKey(), share);
                    }
                    // 没有被抢完的红包金额退回到发起者的账户
                    accountMapper.increaseBalance(publisherId, amount);
                    // 通过为红包结果key设置过期时间，保证结算处理的幂等性
                    try {
                        redisTemplate.expire(resultKey, 3600, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            log.info("红包{}结算完成", key);
        }
    }
}
