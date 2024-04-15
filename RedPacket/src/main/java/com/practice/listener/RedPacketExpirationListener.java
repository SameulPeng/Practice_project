package com.practice.listener;

import com.practice.common.logging.ExtLogger;
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
 * Redis消息监听器，监听key过期事件并进行回调<br/>
 * 需要在Redis服务器端启用key过期事件功能
 */
@Component
@Profile("redis")
@ConditionalOnProperty("spring.redis.key-listener")
public class RedPacketExpirationListener extends KeyExpirationEventMessageListener {
    private static final ExtLogger log = ExtLogger.create(RedPacketExpirationListener.class); // 日志Logger对象
    private RedPacketService redPacketService;
    private RedPacketExtensionComposite extensionComposite; // 抢红包业务扩展组合类
    private RedPacketProperties redPacketProperties; // 配置参数类

    @Autowired
    private void setRedPacketService(RedPacketService redPacketService) {
        this.redPacketService = redPacketService;
    }

    @Autowired
    private void setExtensionComposite(RedPacketExtensionComposite extensionComposite) {
        this.extensionComposite = extensionComposite;
    }

    @Autowired
    private void setRedPacketProperties(RedPacketProperties redPacketProperties) {
        this.redPacketProperties = redPacketProperties;
    }

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
            log.biz("[{}] [ ] 红包过期未被抢完", key);
            // 执行红包结束且未抢完时的扩展方法
            extensionComposite.onExpire(key);
        } else if (key.startsWith(resultPrefix)) {
            // 去除红包结果key前缀
            key = key.substring(keyPrefix.length());
            log.biz("[{}] [ ] 红包最终结束，清理结果", key);
            // 从本地缓存中移除
            redPacketService.removeFromCache(key.substring(resultPrefix.length()));
            // 执行红包结果移除时的扩展方法
            extensionComposite.onRemove(key);
        }
    }
}
