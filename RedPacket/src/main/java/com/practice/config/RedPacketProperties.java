package com.practice.config;

import com.practice.common.exception.IllegalPropertyException;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 配置参数类
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "red-packet")
public class RedPacketProperties {
    private int serviceId; // JVM编号，取值范围为0到4095
    private SqlBatch settlementSqlBatch = SqlBatch.PREPARED; // 红包结算处理时的SQL批量发送方式
    private String dateTimePattern = "yyyy-MM-dd HH:mm:ss"; // 发起抢红包响应中的日期时间格式
    private Biz biz; // 业务相关参数
    private Publish publish; // 发起抢红包过程相关参数
    private Share share; // 参与抢红包过程相关参数

    /**
     * 配置参数合法性校验
     */
    @PostConstruct
    private void init() throws IllegalPropertyException {
        if (biz.getKeyPrefix().length() > 64
                || !isAllPrintableAscii(biz.getKeyPrefix())) {
            throw new IllegalPropertyException("红包Key前缀设置有误，请设置在64个字符以内，并且只包含ASCII编码可打印字符：red-packet.biz.key-prefix");
        }

        if (biz.getResultPrefix().length() > 64
                || !isAllPrintableAscii(biz.getResultPrefix())) {
            throw new IllegalPropertyException("红包结果Key前缀设置有误，请设置在64个字符以内，并且只包含ASCII编码可打印字符：red-packet.biz.result-prefix");
        }

        if (biz.getResultPlaceholder().length() > 64
                || !isAllPrintableAscii(biz.getResultPlaceholder())) {
            throw new IllegalPropertyException("红包结果Key占位项设置有误，请设置在64个字符以内，并且只包含ASCII编码可打印字符：red-packet.biz.result-placeholder");
        }

        if (biz.getMinAmount() > biz.getMaxAmount()
                || biz.getMinAmount() < 1
                || biz.getMaxAmount() > 0x3fffffff) {
            throw new IllegalPropertyException("红包金额范围设置有误：red-packet.biz.max-expire-time 或 red-packet.biz.min-expire-time");
        }

        if (biz.getMinShareNum() > biz.getMaxShareNum()
                || biz.getMinShareNum() < 1
                || biz.getMaxShareNum() > 0x3ff) {
            throw new IllegalPropertyException("红包份数范围设置有误：red-packet.biz.max-share-num 或 red-packet.biz.min-share-num");
        }

        if (biz.getMinExpireTime() > biz.getMaxExpireTime()
                || biz.getMinExpireTime() < 1
                || biz.getMaxExpireTime() > 0xffffff) {
            throw new IllegalPropertyException("红包有效期范围设置有误：red-packet.biz.max-amount 或 red-packet.biz.min-amount");
        }

        if (biz.getResultKeepTime() < 1
                || biz.getResultKeepTime() > 2592000) {
            throw new IllegalPropertyException("红包结果保留时长设置有误：red-packet.biz.result-keep-time");
        }
    }

    /**
     * 检验字符串是否只包含ASCII编码可打印字符（编码范围为32到127）
     * @param str 待检测字符串
     * @return 检测结果
     */
    private boolean isAllPrintableAscii(String str) {
        char[] chars = str.toCharArray();
        boolean result = true;
        for (char c : chars) {
            if (c < 0x20 || c > 0x7F) {
                result = false;
                break;
            }
        }
        return result;
    }

    /**
     * 业务相关参数
     */
    @Getter
    @Setter
    public static class Biz {
        private String keyPrefix; // 红包key前缀，最多64个字符，且只能包含ASCII编码可打印字符（编码范围为32到127）
        private String resultPrefix; // 红包结果key前缀，最多64个字符，且只能包含ASCII编码可打印字符（编码范围为32到127）
        private String resultPlaceholder; // 红包结果key占位项，最多64个字符，且只能包含ASCII编码可打印字符（编码范围为32到127）
        private int maxAmount; // 红包最大金额，单位为分，上限为1073741823，约一千万元
        private int minAmount; // 红包最小金额，单位为分，上限为1073741823，约一千万元
        private int maxShareNum; // 红包最大份数，上限为1023
        private int minShareNum; // 红包最小份数，上限为1023
        private int maxExpireTime; // 红包最长有效期，单位为秒，上限为16777215，约194天
        private int minExpireTime; // 红包最短有效期，单位为秒，上限为16777215，约194天
        private int resultKeepTime = 3600; // 红包结算后结果保留时长，单位为秒，上限为2592000，即30天
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
        private int atomicMaxCount = 128; // 原子整数数量上限
        private int atomicLeakPatrolInterval = 86400; // 原子整数Map泄漏检查时间间隔，单位为秒
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
        private int timeout = 3000; // 抢红包响应超时，单位为毫秒
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
