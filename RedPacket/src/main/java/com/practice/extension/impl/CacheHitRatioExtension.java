package com.practice.extension.impl;

import com.practice.common.annotation.ExtensionPriority;
import com.practice.common.logging.ExtLogger;
import com.practice.common.result.RedPacketResult;
import com.practice.common.result.ShareResult;
import com.practice.common.util.DateTimeUtil;
import com.practice.config.RedPacketProperties;
import com.practice.extension.RedPacketExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存命中率统计和定时计算扩展类
 */
@Component
@Profile({"biz-dev", "biz-test" ,"biz-prod"})
@ExtensionPriority(5)
@ConditionalOnProperty("red-packet.share.cache-hit-ratio-stats")
public class CacheHitRatioExtension implements RedPacketExtension {
    private final ExtLogger log = ExtLogger.create(CacheHitRatioExtension.class); // 日志Logger对象
    private RedPacketProperties redPacketProperties; // 属性配置类
    private final DateTimeFormatter dateTimeFormatter = // 日期时间的格式化类，线程安全
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /**
     * 有效红包访问总次数
     */
    private final AtomicLong totalCount = new AtomicLong(0);
    /**
     * 获取结果总次数
     */
    private final AtomicLong resultCount = new AtomicLong(0);
    /**
     * 缓存未命中次数
     */
    private final AtomicLong missCount = new AtomicLong(0);

    @Autowired
    private void setRedPacketProperties(RedPacketProperties redPacketProperties) {
        this.redPacketProperties = redPacketProperties;
    }

    @PostConstruct
    private void init() {
        // 统计线程，定期计算并输出缓存命中率
        ScheduledExecutorService scheduledPool =
                Executors.newScheduledThreadPool(1, r -> new Thread(r, "CacheHitRatioTeller"));
        int interval = redPacketProperties.getShare().getCacheHitRatioCheckInterval();
        scheduledPool.scheduleAtFixedRate(new Runnable() {
            private long lastTimestamp = System.currentTimeMillis();
            private long lastTotals = totalCount.get();
            private long lastResults = resultCount.get();
            private long lastMisses = missCount.get();

            @Override
            public void run() {
                long timestamp = System.currentTimeMillis();

                // 获取有效红包访问总次数、获取结果次数、缓存未命中次数
                long totals = totalCount.get();
                long results = resultCount.get();
                long misses = missCount.get();

                // 获取时间段内变化值
                long totalsDelta = totals - lastTotals;
                long resultsDelta = results - lastResults;
                long missesDelta = misses - lastMisses;

                // 计算并输出缓存命中率
                tell(lastTimestamp, timestamp, totalsDelta, totals, resultsDelta, results, missesDelta, misses);

                lastTimestamp = timestamp;
                lastTotals = totals;
                lastResults = results;
                lastMisses = misses;
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    @Override
    public Map<String, Object> onCache(Map<String, Object> mapResult) {
        // 红包结果写入缓存时，表示缓存未命中，缓存未命中次数增加
        missCount.getAndIncrement();
        return mapResult;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public RedPacketResult afterShare(String key, String userId, RedPacketResult redPacketResult) {
        ShareResult shareResult = (ShareResult) redPacketResult.getResult();
        if (shareResult.getStatus() != 0 || shareResult.getMapResult() != null) {
            // 排除无效红包访问，有效红包访问次数增加
            totalCount.getAndIncrement();
            // 如果有效红包访问中携带红包结果，获取结果次数增加
            if (shareResult.getMapResult() != null) resultCount.getAndIncrement();
        }
        return redPacketResult;
    }

    /**
     * 计算并输出缓存命中率
     * @param fromTimestamp 上一次统计毫秒时间戳
     * @param toTimestamp 本次统计毫秒时间戳
     * @param totals 有效红包访问总次数
     * @param results 获取结果次数
     * @param misses 缓存未命中次数
     */
    private void tell(long fromTimestamp, long toTimestamp, long totalsDelta, long totals, long resultsDelta, long results, long missesDelta, long misses) {
        // 获取结果率 = 获取结果次数 / 有效红包访问总次数
        // 如果此比率很小，则参与抢红包的用户竞争不激烈，且较少查看红包结果
        double resultRatio = totals == 0 ? 0 : results * 1d / totals;
        double resultDeltaRatio = totalsDelta == 0 ? 0 : resultsDelta * 1d / totalsDelta;
        // 缓存命中率 = 1 - 缓存未命中次数 / 获取结果次数
        double cacheHitRatio = results == 0 ? 0 : (1 - misses * 1d / results);
        double cacheHitDeltaRatio = resultsDelta == 0 ? 0 : (1 - missesDelta * 1d / resultsDelta);

        // 将毫秒时间戳转换为日期时间格式
        String from = DateTimeUtil.millis2DateTime(dateTimeFormatter, fromTimestamp);
        String to = DateTimeUtil.millis2DateTime(dateTimeFormatter, toTimestamp);

        // 输出统计数据，分别为总结果率、总缓存命中率、当前周期结果率、当前周期缓存命中率
        log.biz(String.format("from %s to %s: {result ratio: %.2f, cache hit ratio: %.2f, result delta ratio: %.2f, cache hit delta ratio: %.2f}",
                from, to, resultRatio, cacheHitRatio, resultDeltaRatio, cacheHitDeltaRatio));
    }
}
