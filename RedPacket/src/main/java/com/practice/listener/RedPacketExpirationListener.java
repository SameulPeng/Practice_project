package com.practice.listener;

import com.practice.config.RedPacketProperties;
import com.practice.extension.RedPacketExtensionComposite;
import com.practice.service.RedPacketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Redis消息监听器，监听key过期事件并进行回调
 */
@Component
@Profile("biz")
@ConditionalOnProperty("spring.redis.key-listener")
public class RedPacketExpirationListener extends KeyExpirationEventMessageListener {
    @Autowired
    private RedPacketService redPacketService;
    @Autowired
    private RedPacketExtensionComposite extensionComposite; // 抢红包业务扩展组合类
    @Autowired
    private RedPacketProperties redPacketProperties; // 配置参数类

    public RedPacketExpirationListener(RedisMessageListenerContainer listenerContainer) {
        super(listenerContainer);
    }

    /**
     * 监听到Redis的key过期事件时的处理
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = message.toString();
        String keyPrefix = redPacketProperties.getBiz().getKeyPrefix();
        String resultPrefix = redPacketProperties.getBiz().getResultPrefix();

        if (key.startsWith(keyPrefix)) {
            // 去除红包key前缀
            key = key.substring(keyPrefix.length());
            // 从原子整数Map中移除
            redPacketService.removeFromAtomicMap(key);
            // 执行红包结束且未抢完时的扩展方法
            extensionComposite.onExpire(key);
        } else if (key.startsWith(resultPrefix)) {
            // 去除红包结果key前缀
            key = key.substring(keyPrefix.length());
            // 从本地缓存中移除
            redPacketService.removeFromCache(key.substring(resultPrefix.length()));
            // 执行红包结果移除时的扩展方法
            extensionComposite.onRemove(key);
        }
    }
}
