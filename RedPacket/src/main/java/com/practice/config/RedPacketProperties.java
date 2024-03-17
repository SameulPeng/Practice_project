package com.practice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 配置参数类
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "red-packet")
public class RedPacketProperties {
    private String serviceId; // JVM编号
    private SqlBatch settlementSqlBatch = SqlBatch.PREPARED; // 红包结算处理时的SQL批量发送方式
    private String dateTimePattern = "yyyy-MM-dd HH:mm:ss"; // 发起抢红包响应中的日期时间格式
    private Biz biz; // 业务相关参数
    private Publish publish; // 发起抢红包过程相关参数
    private Share share; // 参与抢红包过程相关参数

    /**
     * 业务相关参数
     */
    @Getter
    @Setter
    public static class Biz {
        private String keyPrefix = "RedPacket:"; // 红包key前缀
        private String resultPrefix = "Result:"; // 红包结果key前缀
        private String resultPlaceholder = "%%%%%%%%%%for_refund%%%%%%%%%%"; // 红包结果key占位项
        private int maxExpireTime = 3600; // 红包最长有效期，单位为秒
        private int minExpireTime = 10; // 红包最短有效期，单位为秒
        private int maxAmount = 500000; // 红包最大金额，单位为分
        private int minAmount = 10; // 红包最小金额，单位为分
        private int resultKeepTime = 3600; // 红包结算后结果保留时长，单位为秒
    }

    /**
     * 发起抢红包过程相关参数
     */
    @Getter
    @Setter
    public static class Publish {
        private int minThreads = 5; // 发起抢红包线程池核心线程数
        private int maxThreads = 200; // 发起抢红包线程池最大线程数
        private int queueSize = 2048; // 发起抢红包线程池阻塞队列大小
        private int atomicMapSize = 128; // 原子整数Map大小
        private int atomicLeakPatrolInterval = 3600; // 原子整数Map泄漏检查时间间隔，单位为秒
    }

    /**
     * 参与抢红包过程相关参数
     */
    @Getter
    @Setter
    public static class Share {
        private int minThreads = 5; // 参与抢红包线程池核心线程数
        private int maxThreads = 200; // 参与抢红包线程池最大线程数
        private int maxTryTimes = 3; // 抢红包错误重试次数
        private int timeout = 5000; // 抢红包响应超时，单位为毫秒
        private int cacheSize = 512; // 本地缓存大小
    }

    /**
     * 红包结算处理时的SQL批量发送方式
     */
    public enum SqlBatch {
        NON_BATCHED, // 逐条发送
        NON_PREPARED, // 批量发送，但不进行服务端预编译
        PREPARED // 批量发送，且进行服务端预编译
    }
}
