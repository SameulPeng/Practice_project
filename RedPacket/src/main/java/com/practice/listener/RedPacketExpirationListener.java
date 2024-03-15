package com.practice.listener;

import com.practice.extension.RedPacketExtensionComposite;
import com.practice.service.RedPacketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Redis消息监听器，监听key过期事件并进行回调
 */
@Component
public class RedPacketExpirationListener extends KeyExpirationEventMessageListener {
    @Autowired
    private RedPacketService redPacketService;
    @Autowired
    private RedPacketExtensionComposite extensionComposite; // 抢红包业务扩展组合类
    @Value("${red-packet.key.prefix}")
    private String RED_PACKET_KEY_PREFIX; // Redis中的红包key的业务前缀
    @Value("${red-packet.key.result-prefix}")
    private String RESULT_KEY_PREFIX; // Redis中的红包结果key的业务前缀

    public RedPacketExpirationListener(RedisMessageListenerContainer listenerContainer) {
        super(listenerContainer);
    }

    /**
     * 监听到Redis的key过期事件时的处理
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = message.toString();
        if (key.startsWith(RED_PACKET_KEY_PREFIX)) {
            // 去除红包key前缀，从原子整数Map中移除
            redPacketService.removeFromAtomicMap(key.substring(RED_PACKET_KEY_PREFIX.length()));
            // 执行红包结束且未抢完时的扩展方法
            extensionComposite.onExpire(key);
        } else if (key.startsWith(RESULT_KEY_PREFIX)) {
            // 去除红包结果key前缀，从原子整数Map中移除
            redPacketService.removeFromCache(key.substring(RESULT_KEY_PREFIX.length()));
            // 执行红包结果移除时的扩展方法
            extensionComposite.onRemove(key);
        }
    }
}
