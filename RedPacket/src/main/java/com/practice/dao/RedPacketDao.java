package com.practice.dao;

import com.practice.common.result.ShareResult;
import com.practice.config.RedPacketProperties;
import com.practice.util.RedPacketKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.util.ClassUtils;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.*;

@Slf4j
@Repository
@Profile("biz")
public class RedPacketDao {
    @Autowired
    @SuppressWarnings("rawtypes")
    private RedisTemplate redisTemplate;
    @Autowired
    private RedPacketProperties redPacketProperties; // 配置参数类
    private String shareScript; // Redis抢红包Lua脚本
    private ThreadPoolExecutor pool; // 控制Redis抢红包响应超时的线程池

    @PostConstruct
    private void init() {
        // 初始化线程池
        this.pool = new ThreadPoolExecutor(
                redPacketProperties.getPublish().getMinThreads(),
                redPacketProperties.getPublish().getMaxThreads(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> new Thread(r, "FutureHandler")
        );

        // 初始化Redis抢红包Lua脚本
        try (BufferedReader br = new BufferedReader(new FileReader(
                ClassUtils.getDefaultClassLoader().getResource("").getPath() + "lua/share.lua"))) {
            char[] chars = new char[512];
            int len;
            StringBuilder sb = new StringBuilder();
            while ((len = br.read(chars)) != -1) {
                sb.append(chars, 0, len);
            }
            shareScript = sb.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 发起抢红包
     * @param key 红包key
     * @param shares 大红包拆分后的若干小红包金额，单位为分
     * @param expireTime 红包过期时长，单位为秒
     */
    @SuppressWarnings("unchecked")
    public void publish(String key, String[] shares, int expireTime) {
        /*
            拼接Lua脚本，目标：
            redis.call('rpush', KEYS[1], 'share1', 'share2', ...)
            redis.call('expire', KEYS[1], ARGV[1])
            redis.call('hset', KEYS[2], '占位项', '0')
         */
        StringBuilder sb = new StringBuilder("redis.call('rpush', KEYS[1]");
        for (String share : shares) {
            sb.append(", '").append(share).append("'");
        }
        sb.append(") redis.call('expire', KEYS[1], ARGV[1])")
                // 预生成红包结果key，保证即使没有用户参与抢红包也能结算退款
                .append(" redis.call('hset', KEYS[2], '").append(redPacketProperties.getBiz().getResultPlaceholder()).append("', '0')");

        String redPacketKey = redPacketProperties.getBiz().getKeyPrefix() + key;
        String resultKey = redPacketProperties.getBiz().getResultPrefix() + key;

        redisTemplate.execute(new DefaultRedisScript<>(sb.toString(), String.class),
                Arrays.asList(redPacketKey, resultKey), String.valueOf(expireTime));

        log.info("红包key创建成功，有效期 {} 秒： {} ", expireTime, key);
    }

    /**
     * 参与抢红包
     * @param key 红包key
     * @param userId 用户ID
     * @return 抢红包结果
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public ShareResult share(String key, String userId) {
        String redPacketKey = redPacketProperties.getBiz().getKeyPrefix() + key;
        String resultKey = redPacketProperties.getBiz().getResultPrefix() + key;

        // 从红包key中提取发起时间，计算抢到红包的耗时
        long timeCost = System.currentTimeMillis() - RedPacketKeyUtil.parseTimestamp(key);
        // 对进行抢红包耗时进行编码
        String encodedTimeCost = RedPacketKeyUtil.encodeTimeCost(timeCost);

        // 通过Future进行响应超时控制，防止长时间等待造成锁阻塞
        FutureTask<String> future = new FutureTask<>(() ->
                (String) redisTemplate.execute(new DefaultRedisScript<>(shareScript, String.class),
                        Arrays.asList(redPacketKey, resultKey), userId, encodedTimeCost));
        pool.submit(future);

        String result;
        try {
            result = future.get(redPacketProperties.getShare().getTimeout(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            // 如果超时，直接返回空
            return null;
        }

        if (result == null) return null;
        if (result.contains("-")) {
            // 如果结果为金额耗时格式，表示已经参与过抢红包，解析金额和耗时
            int idx = result.indexOf('-');
            return ShareResult.share(
                    ShareResult.ShareType.FAIL_REDO, null,
                    Integer.parseInt(result.substring(0, idx)),
                    RedPacketKeyUtil.decodeTimeCost(result.substring(idx + 1))
            );
        } else {
            int share = Integer.parseInt(result);
            return share == 0 ?
                    // 如果结果为0，表示抢不到红包或红包结束后的结果查询
                    ShareResult.share(
                            ShareResult.ShareType.FAIL_END,
                            // 查询红包结果
                            redisTemplate.opsForHash().entries(resultKey),
                            -1, -1L
                    )
                    // 如果结果为正整数，表示抢到红包
                    : ShareResult.share(ShareResult.ShareType.SUCCESS_ONGOING, null, share, timeCost);
        }
    }
}
