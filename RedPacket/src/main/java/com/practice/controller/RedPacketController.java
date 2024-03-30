package com.practice.controller;

import com.practice.common.exception.BalanceNotEnoughException;
import com.practice.common.exception.IllegalAccountException;
import com.practice.common.result.PublishResult;
import com.practice.common.result.RedPacketResult;
import com.practice.config.RedPacketProperties;
import com.practice.pojo.RedPacketInfo;
import com.practice.service.RedPacketService;
import com.practice.util.DateTimeUtil;
import com.practice.util.RedPacketKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
@Profile("biz")
public class RedPacketController {
    @Autowired
    private RedPacketService redPacketService;
    @Autowired
    private RedPacketProperties redPacketProperties; // 配置参数类
    private DateTimeFormatter dateTimeFormatter; // 抢红包发起日期时间的格式化类，线程安全

    @PostConstruct
    private void init() {
        dateTimeFormatter = DateTimeFormatter.ofPattern(redPacketProperties.getDateTimePattern());
    }

    /**
     * 发起抢红包
     * @param info 发起抢红包用户ID、红包总金额（单位为分）、拆分小红包份数、红包过期时长（单位为秒）
     */
    @PostMapping("/redpacket/publish")
    @SuppressWarnings("rawtypes")
    public RedPacketResult publish(@RequestBody RedPacketInfo info) {
        String userId = info.getUserId();
        int amount = info.getAmount();
        int shareNum = info.getShareNum();
        int expireTime = info.getExpireTime();
        // 检验总金额设置是否超出范围
        if (amount < redPacketProperties.getBiz().getMinAmount()
                || amount > redPacketProperties.getBiz().getMaxAmount()) {
            log.warn("用户  {}  发起抢红包，总金额  {}  设置不合法", userId, amount);
            return RedPacketResult.error(RedPacketResult.ErrorType.WRONG_AMOUNT);
        }
        // 校验小红包份数是否超出范围
        if (shareNum < redPacketProperties.getBiz().getMinShareNum()
                || shareNum > redPacketProperties.getBiz().getMaxShareNum()
                || shareNum > amount) {
            log.warn("用户 {} 发起抢红包，份数 {} 设置不合法", userId, shareNum);
            return RedPacketResult.error(RedPacketResult.ErrorType.WRONG_SHARE_NUM);
        }
        // 校验有效期设置是否超出范围
        if (expireTime < redPacketProperties.getBiz().getMinExpireTime()
                || expireTime > redPacketProperties.getBiz().getMaxExpireTime()) {
            log.warn("用户 {} 发起抢红包，有效期 {} 设置不合法", userId, expireTime);
            return RedPacketResult.error(RedPacketResult.ErrorType.WRONG_EXPIRE_TIME);
        }

        long timestamp = System.currentTimeMillis();
        // 使用JVM编号、线程ID、用户ID、红包金额、当前时间戳生成红包key
        String key = RedPacketKeyUtil.generateKey(
                redPacketProperties.getServiceId(), Thread.currentThread().getId(),
                amount, expireTime, timestamp, userId
        );

        redPacketService.publish(key, userId, amount, shareNum, expireTime);

        return RedPacketResult.publishSuccess(
                key,
                PublishResult.publishSuccess(
                        DateTimeUtil.millis2DateTime(dateTimeFormatter, timestamp),
                        amount, shareNum, expireTime
                )
        );
    }

    /**
     * 参与抢红包
     * @param key 红包key
     * @param userId 抢红包用户ID
     */
    @GetMapping("/redpacket/share")
    @SuppressWarnings("rawtypes")
    public RedPacketResult share(@RequestParam String key, @RequestParam String userId) {
        return redPacketService.share(key, userId);
    }

    /**
     * 处理发起抢红包的余额不足的异常
     */
    @ExceptionHandler(BalanceNotEnoughException.class)
    @SuppressWarnings("rawtypes")
    public RedPacketResult balanceNotEnough(BalanceNotEnoughException e) {
        String userId = e.getUserId();
        float amount = e.getAmount() / 100f;
        log.warn("用户 {} 余额不足 {} 元, 抢红包发起失败", userId, amount);
        return RedPacketResult.error(RedPacketResult.ErrorType.BALANCE_NOT_ENOUGH);
    }

    /**
     * 处理发起抢红包的账户不存在的异常
     */
    @ExceptionHandler(IllegalAccountException.class)
    @SuppressWarnings("rawtypes")
    public RedPacketResult illegalAccount(IllegalAccountException e) {
        String userId = e.getUserId();
        log.warn("用户 {} 账户不存在，抢红包发起失败", userId);
        return RedPacketResult.error(RedPacketResult.ErrorType.ILLEGAL_ACCOUNT);
    }

    /**
     * 处理未知异常
     */
    @ExceptionHandler(Throwable.class)
    @SuppressWarnings("rawtypes")
    public RedPacketResult unhandled(Throwable e) {
        log.error("未处理异常： {} ", e.getMessage());
        return RedPacketResult.error(RedPacketResult.ErrorType.UNKNOWN_ERROR);
    }
}
