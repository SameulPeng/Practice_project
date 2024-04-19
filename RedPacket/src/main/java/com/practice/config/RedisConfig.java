package com.practice.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Redis配置类
 */
@Configuration
@Profile({"redis-dev", "redis-test", "redis-prod"})
public class RedisConfig {
    /**
     * 注册一个Redis消息监听器容器组件，用于维护Redis消息监听器
     */
    @Bean
    @ConditionalOnProperty("spring.redis.key-listener")
    RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory redisConnectionFactory) {
        RedisMessageListenerContainer listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(redisConnectionFactory);
        return listenerContainer;
    }

    /**
     * 注册RedisTemplate组件，配置序列化
     */
    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public RedisTemplate redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate redisTemplate = new RedisTemplate();
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // 设置连接工厂
        redisTemplate.setConnectionFactory(factory);
        // 设置键序列化类
        redisTemplate.setKeySerializer(stringRedisSerializer);
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        // 设置值序列化类
        redisTemplate.setValueSerializer(stringRedisSerializer);
        redisTemplate.setHashValueSerializer(stringRedisSerializer);

        redisTemplate.afterPropertiesSet();

        return redisTemplate;
    }

    /**
     * 注册Redisson组件
     */
    @Bean
    public RedissonClient redisson(@Value("${spring.redis.redisson.file}") String configPath) {
        Config config;
        InputStream is = null;
        try {
            // 根据配置文件路径类型，获取jar包内或jar包外配置文件的输入流
            if (configPath.startsWith("classpath:")) {
                configPath = configPath.substring("classpath:".length());
                is = this.getClass().getClassLoader().getResourceAsStream(configPath);
            } else {
                is = new FileInputStream(configPath);
            }
            // 通过输入流构建Redisson配置
            config = Config.fromYAML(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignore) {}
            }
        }
        return Redisson.create(config);
    }
}
