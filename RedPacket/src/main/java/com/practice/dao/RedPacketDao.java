package com.practice.dao;

import com.practice.common.result.ShareResult;
import com.practice.common.util.RedPacketKeyUtil;
import com.practice.config.RedPacketProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

@Repository
@Profile({"biz-dev", "biz-test" ,"biz-prod"})
public class RedPacketDao {
    @SuppressWarnings("rawtypes")
    private RedisTemplate redisTemplate;
    private RedPacketProperties redPacketProperties; // 配置参数类
    private String shareScript; // Redis抢红包Lua脚本
    private ThreadPoolExecutor pool; // 控制Redis抢红包响应超时的线程池

    @Autowired
    @SuppressWarnings("rawtypes")
    private void setRedisTemplate(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Autowired
    private void setRedPacketProperties(RedPacketProperties redPacketProperties) {
        this.redPacketProperties = redPacketProperties;
    }

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
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("lua/share.lua"))))) {
            char[] chars = new char[512];
            int len;
            StringBuilder sb = new StringBuilder();
            while ((len = br.read(chars)) != -1) {
                sb.append(chars, 0, len);
            }
            shareScript = sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 发起抢红包
     * @param key 红包key
     * @param shares 大红包拆分后的若干小红包金额，单位为分
     * @param expireTime 红包过期时长，单位为秒
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void publish(String key, String[] shares, int expireTime) {
        String redPacketKey = redPacketProperties.getBiz().getKeyPrefix() + key;
        String resultKey = redPacketProperties.getBiz().getResultPrefix() + key;
        String resultPlaceholder = redPacketProperties.getBiz().getResultPlaceholder();

        byte[][] bss = new byte[shares.length][];
        for (int i = 0; i < shares.length; i++) {
            bss[i] = shares[i].getBytes(StandardCharsets.UTF_8);
        }
        byte[] redPacketKeyBytes = redPacketKey.getBytes(StandardCharsets.UTF_8);
        byte[] resultKeyBytes = resultKey.getBytes(StandardCharsets.UTF_8);
        byte[] resultPlaceholderBytes = resultPlaceholder.getBytes(StandardCharsets.UTF_8);

        // 使用管道操作，合并为单次请求
        // 创建红包key，并设置过期时间
        // 使用占位项预生成红包结果key，保证即使没有用户参与抢红包也能结算退款
        List list = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            // 结果为红包份数
            connection.listCommands().rPush(redPacketKeyBytes, bss);
            // 结果为真
            connection.expire(redPacketKeyBytes, expireTime);
            // 结果为真
            connection.hashCommands().hSet(resultKeyBytes, resultPlaceholderBytes, "0".getBytes(StandardCharsets.UTF_8));
            return null;
        });

        // 检查管道操作结果，如果结果有误则抛出异常
        if (list.size() != 3
                || (Long) list.get(0) != shares.length
                || !(Boolean) list.get(1)
                || !(Boolean) list.get(2)) {
            throw new RuntimeException();
        }
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
            future.cancel(true);
            return null;
        }

        if (result == null) return null;
        if (result.contains("-")) {
            // 如果结果为金额耗时格式，表示已经参与过抢红包，解析金额和耗时
            int idx = result.indexOf('-');
            return ShareResult.share(
                    ShareResult.ShareType.FAIL_REDO,
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
                            redisTemplate.opsForHash().entries(resultKey)
                    )
                    // 如果结果为正整数，表示抢到红包
                    : ShareResult.share(ShareResult.ShareType.SUCCESS_ONGOING, share, timeCost);
        }
    }

    /**
     * 移除红包结果key<br/>
     * 此方法在消息发送失败时被调用，移除没有过期时间的无效红包结果key，避免泄漏
     * @param key 红包key
     */
    @SuppressWarnings("unchecked")
    public void removeResult(String key) {
        String resultKey = redPacketProperties.getBiz().getResultPrefix() + key;
        redisTemplate.delete(resultKey);
    }
}
