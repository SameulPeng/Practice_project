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
    /**
     * JVM编号，取值范围为0到4095
     */
    private int serviceId;
    /**
     * 红包结算处理时的SQL批量发送方式
     */
    private SqlBatch settlementSqlBatch = SqlBatch.PREPARED;
    /**
     * 发起抢红包响应中的日期时间格式
     */
    private String dateTimePattern = "yyyy-MM-dd HH:mm:ss";
    /**
     * 业务相关参数
     */
    private Biz biz;
    /**
     * 发起抢红包过程相关参数
     */
    private Publish publish;
    /**
     * 参与抢红包过程相关参数
     */
    private Share share;

    /**
     * 配置参数合法性校验
     */
    @PostConstruct
    private void init() throws IllegalPropertyException {
        if (biz.getKeyPrefix() == null
                || biz.getKeyPrefix().length() > 0x40
                || !isAllPrintableAscii(biz.getKeyPrefix())) {
            throw new IllegalPropertyException("红包Key前缀设置有误，请设置在64个字符以内，并且只包含ASCII编码可打印字符：red-packet.biz.key-prefix");
        }

        if (biz.getResultPrefix() == null
                || biz.getResultPrefix().length() > 0x40
                || !isAllPrintableAscii(biz.getResultPrefix())) {
            throw new IllegalPropertyException("红包结果Key前缀设置有误，请设置在64个字符以内，并且只包含ASCII编码可打印字符：red-packet.biz.result-prefix");
        }

        if (biz.getResultPlaceholder() == null
                || biz.getResultPlaceholder().length() > 0x40
                || !isAllPrintableAscii(biz.getResultPlaceholder())) {
            throw new IllegalPropertyException("红包结果Key占位项设置有误，请设置在64个字符以内，并且只包含ASCII编码可打印字符：red-packet.biz.result-placeholder");
        }

        if (biz.getMinAmount() > biz.getMaxAmount()
                || biz.getMinAmount() < 1
                || biz.getMaxAmount() > 0x3FFFFFFF) {
            throw new IllegalPropertyException("红包金额范围设置有误：red-packet.biz.max-expire-time 或 red-packet.biz.min-expire-time");
        }

        if (biz.getMinShareNum() > biz.getMaxShareNum()
                || biz.getMinShareNum() < 1
                || biz.getMaxShareNum() > 0x3FF) {
            throw new IllegalPropertyException("红包份数范围设置有误：red-packet.biz.max-share-num 或 red-packet.biz.min-share-num");
        }

        if (biz.getMinExpireTime() > biz.getMaxExpireTime()
                || biz.getMinExpireTime() < 1
                || biz.getMaxExpireTime() > 0xFFFFFF) {
            throw new IllegalPropertyException("红包有效期范围设置有误：red-packet.biz.max-amount 或 red-packet.biz.min-amount");
        }

        if (biz.getResultKeepTime() < 1
                || biz.getResultKeepTime() > 2592000) {
            throw new IllegalPropertyException("红包结果保留时长设置有误：red-packet.biz.result-keep-time");
        }

        if (biz.getShareRankNum() < 1
                || biz.getShareRankNum() > 0x3FF) {
            throw new IllegalPropertyException("参与抢红包金额排名数量设置有误：red-packet.biz.share-rank-num");
        }

        if (biz.getTimeCostRankNum() < 1
                || biz.getTimeCostRankNum() > 0x3FF) {
            throw new IllegalPropertyException("参与抢红包耗时排名数量设置有误：red-packet.biz.time-cost-rank-num");
        }

        if (settlementSqlBatch != SqlBatch.NON_BATCHED
                && settlementSqlBatch != SqlBatch.NON_PREPARED
                && settlementSqlBatch != SqlBatch.PREPARED) {
            throw new IllegalArgumentException("无法识别的SQL批量发送方式：red-packet.settlement-sql-batch");
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
        /**
         * 红包key前缀<br/>
         * 最多64个字符，且只能包含ASCII编码可打印字符（编码范围为32到127）
         */
        private String keyPrefix = "RedPacket:";
        /**
         * 红包结果key前缀<br/>
         * 最多64个字符，且只能包含ASCII编码可打印字符（编码范围为32到127）
         */
        private String resultPrefix = "Result:";
        /**
         * 红包结果key占位项<br/>
         * 最多64个字符，且只能包含ASCII编码可打印字符（编码范围为32到127）
         */
        private String resultPlaceholder = "%%%%%%%%%%for_refund%%%%%%%%%%";
        /**
         * 红包最大金额，单位为分<br/>
         * 上限为1073741823，约一千万元
         */
        private int maxAmount = 500000;
        /**
         * 红包最小金额，单位为分<br/>
         * 上限为1073741823，约一千万元
         */
        private int minAmount = 10;
        /**
         * 红包最大份数<br/>
         * 上限为1023
         */
        private int maxShareNum = 100;
        /**
         * 红包最小份数<br/>
         * 上限为1023
         */
        private int minShareNum = 1;
        /**
         * 红包最长有效期，单位为秒<br/>
         * 上限为16777215，约194天
         */
        private int maxExpireTime = 3600;
        /**
         * 红包最短有效期，单位为秒<br/>
         * 上限为16777215，约194天
         */
        private int minExpireTime = 10;
        /**
         * 红包结算后结果保留时长，单位为秒<br/>
         * 上限为2592000，即30天
         */
        private int resultKeepTime = 3600;
        /**
         * 参与抢红包金额排名数量，即显示抢红包金额最大的前若干名<br/>
         * 上限为1023
         */
        private int shareRankNum = 10;
        /**
         * 参与抢红包耗时排名数量，即显示抢红包耗时最短的前若干名<br/>
         * 上限为1023
         */
        private int timeCostRankNum = 10;
    }

    /**
     * 发起抢红包过程相关参数
     */
    @Getter
    @Setter
    public static class Publish {
        /**
         * 发起抢红包线程池核心线程数
         */
        private int minThreads = 5;
        /**
         * 发起抢红包线程池最大线程数
         */
        private int maxThreads = 200;
        /**
         * 发起抢红包线程池阻塞队列大小
         */
        private int queueSize = 2048;
        /**
         * 原子整数数量上限
         */
        private int atomicMapSize = 128;
        /**
         * 原子整数保留时长，单位为秒
         */
        private int atomicKeepTime = 30;
    }

    /**
     * 参与抢红包过程相关参数
     */
    @Getter
    @Setter
    public static class Share {
        /**
         * 参与抢红包线程池核心线程数
         */
        private int minThreads = 5;
        /**
         * 参与抢红包线程池最大线程数
         */
        private int maxThreads = 200;
        /**
         * 抢红包错误重试次数
         */
        private int maxTryTimes = 3;
        /**
         * 抢红包响应超时，单位为毫秒
         */
        private int timeout = 3000;
        /**
         * 本地缓存大小
         */
        private int cacheSize = 512;
        /**
         * 缓存命中率统计时间间隔，单位为秒
         */
        private int cacheHitRatioCheckInterval = 3600;
    }

    /**
     * 红包结算处理时的SQL批量发送方式
     */
    public enum SqlBatch {
        /**
         * 逐条发送
         */
        NON_BATCHED,
        /**
         * 批量发送，但不进行服务端预编译
         */
        NON_PREPARED,
        /**
         * 批量发送，且进行服务端预编译
         */
        PREPARED
    }
}
