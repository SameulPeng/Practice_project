package com.practice.dao;

import com.practice.common.result.ShareResult;
import com.practice.util.RedPacketKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.util.ClassUtils;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;

@Repository
@Slf4j
public class RedPacketDao {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    @SuppressWarnings("rawtypes")
    private RedisTemplate redisTemplate;
    @Value("${red-packet.key.prefix}")
    private String RED_PACKET_KEY_PREFIX; // Redis中的红包key的业务前缀
    @Value("${red-packet.key.result-prefix}")
    private String RESULT_KEY_PREFIX; // Redis中的红包结果key的业务前缀
    @Value("${red-packet.key.result-placeholder}")
    private String RESULT_KEY_PLACEHOLDER; // 预生成红包结果key所使用的占位项
    private String shareScript; // Redis抢红包Lua脚本
    @Value("${red-packet.share.timeout}")
    private long shareTimeout; // Redis抢红包的响应超时时间
    @Value("${red-packet.share.threads.min}")
    private int corePoolSize; // 线程池核心线程数
    @Value("${red-packet.share.threads.max}")
    private int maxPoolSize; // 线程池最大线程数
    private ThreadPoolExecutor pool; // 控制Redis抢红包响应超时的线程池

    @PostConstruct
    private void init() {
        // 初始化线程池
        this.pool = new ThreadPoolExecutor(corePoolSize, maxPoolSize, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> new Thread(r, "FutureHandler"));
        // 初始化Redis抢红包Lua脚本
        try (BufferedReader br = new BufferedReader(new FileReader(
                ClassUtils.getDefaultClassLoader().getResource("").getPath() + "lua/share.lua"))) {
            char[] chars = new char[512];
            int len;
            StringBuilder sb = new StringBuilder();
            while ((len = br.read(chars)) != -1) {
                sb.append(String.valueOf(chars, 0, len));
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
                .append(" redis.call('hset', KEYS[2], '").append(RESULT_KEY_PLACEHOLDER).append("', '0')");

        String redPacketKey = RED_PACKET_KEY_PREFIX + key;
        String resultKey = RESULT_KEY_PREFIX + key;

        stringRedisTemplate.execute(new DefaultRedisScript<>(sb.toString()), Arrays.asList(redPacketKey, resultKey), String.valueOf(expireTime));

        log.info("抢红包发起成功，有效期{}秒：{}", expireTime, key);
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
        String redPacketKey = RED_PACKET_KEY_PREFIX + key;
        String resultKey = RESULT_KEY_PREFIX + key;

        // 通过Future进行响应超时控制，防止无限等待造成死锁
        FutureTask<Long> future = new FutureTask<>(() ->
                (Long) redisTemplate.execute(new DefaultRedisScript<>(shareScript, Long.class),
                        Arrays.asList(redPacketKey, resultKey), userId));
        pool.submit(future);

        Long result;
        try {
            result = future.get(shareTimeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            // 如果超时，直接返回空
            return null;
        }

        if (result == 0) {
            // 如果结果为0，表示抢不到红包或红包结束后的结果查询
            Map<String, String> mapResult = redisTemplate.opsForHash().entries(resultKey);
            return ShareResult.shareFail(null, mapResult);
        } else {
            return result == -1 ?
                    // 如果结果为-1，表示已经参与过抢红包
                    ShareResult.shareRedo("您已经抢到过红包，请等待结束")
                    // 如果结果为正整数，表示抢到红包，从红包key中提取发起时间，计算抢到红包的耗时
                    : ShareResult.shareSuccess("您抢到红包，金额为" + result, null, System.currentTimeMillis() - RedPacketKeyUtil.parseTimestamp(key));
        }
    }
}
