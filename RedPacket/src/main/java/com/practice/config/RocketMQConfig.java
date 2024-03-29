package com.practice.config;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * RocketMQ配置类
 */
@Configuration
@Profile("rocketmq")
public class RocketMQConfig {
    @Bean
    public RocketMQTemplate rocketMQTemplate(@Value("${rocketmq.name-server}") String nameServer) {
        RocketMQTemplate rocketMQTemplate = new RocketMQTemplate();
        // 创建结算消息生产者
        DefaultMQProducer producer = new DefaultMQProducer("RedPacketMQProducer");
        // 指定命名服务器IP地址和端口号
        producer.setNamesrvAddr(nameServer);
        // 设置结算消息生产者
        rocketMQTemplate.setProducer(producer);
        return rocketMQTemplate;
    }
}
