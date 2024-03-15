package com.practice.controller;

import com.practice.common.result.PublishResult;
import com.practice.common.result.RedPacketResult;
import com.practice.common.result.ShareResult;
import com.practice.pojo.RedPacketInfo;
import com.practice.service.RedPacketService;
import com.practice.util.RedPacketKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
public class RedPacketController {
    @Autowired
    private RedPacketService redPacketService;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); // 抢红包发起日期时间的格式化
    @Value("${red-packet.service-id}")
    private String serviceId; // JVM编号
    @Value("${red-packet.max-expire-time}")
    private int maxExpireTime; // 红包最长有效期，单位为秒
    @Value("${red-packet.min-expire-time}")
    private int minExpireTime; // 红包最短有效期，单位为秒

    /**
     * 发起抢红包
     * @param info 红包总金额（单位为分）、拆分小红包份数、红包过期时长（单位为秒）
     */
    @PostMapping("/redpacket/publish")
    public RedPacketResult<PublishResult> publish(@RequestBody RedPacketInfo info) {
        String userId = info.getUserId();
        int amount = info.getAmount();
        int shareNum = info.getShareNum();
        int expireTime = info.getExpireTime();
        // 校验有效期设置是否超出范围
        if (expireTime > maxExpireTime || expireTime < minExpireTime) {
            return RedPacketResult.error("有效期设置超出范围，抢红包发起失败");
        }
        // 使用JVM编号、线程ID、用户ID、红包金额、当前时间戳生成红包key
        long timestamp = System.currentTimeMillis();
        String key = RedPacketKeyUtil.generateKey(serviceId, Thread.currentThread().getId(), userId, amount, timestamp);

        redPacketService.publish(key, userId, amount, shareNum, expireTime);

        return RedPacketResult.publishSuccess(key, PublishResult.publishSuccess(
                dateTimeFormatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())),
                amount, shareNum, expireTime
        ));
    }

    /**
     * 参与抢红包
     * @param key 红包key
     * @param userId 抢红包用户ID
     */
    @GetMapping("/redpacket/share")
    public RedPacketResult<ShareResult> share(@RequestParam String key, @RequestParam String userId) {
        return redPacketService.share(key, userId);
    }
}
