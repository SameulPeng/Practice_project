package com.practice.mq.consumer;

import com.practice.common.pojo.BigDataInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 大数据接口消费者端测试类
 */
@Component
@Profile("kafka")
@ConditionalOnProperty("spring.kafka.big-data-test")
public class BigDataMQConsumerTest {
    @KafkaListener(topics = "red-packet-big-data", groupId = "big-data-test")
    public void receive(String msg) {
        System.out.println("\n" + BigDataInfo.decode(msg) + "\n");
    }
}
