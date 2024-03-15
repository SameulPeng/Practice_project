package com.practice.common.exception.handler;

import com.practice.common.exception.BalanceNotEnoughException;
import com.practice.common.exception.IllegalAccountException;
import com.practice.common.result.PublishResult;
import com.practice.common.result.RedPacketResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.DataTruncation;

/**
 * 全局异常处理类
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 处理发起抢红包的余额不足的异常
     */
    @ExceptionHandler(BalanceNotEnoughException.class)
    public RedPacketResult<PublishResult> balanceNotEnough(BalanceNotEnoughException e) {
        log.info("用户{}余额不足{}, 抢红包发起失败", e.getUserId(), e.getAmount());
        return RedPacketResult.error("账户余额不足，抢红包发起失败");
    }

    /**
     * 处理发起抢红包的账户不存在的异常
     */
    @ExceptionHandler(IllegalAccountException.class)
    public RedPacketResult<PublishResult> illegalAccount(IllegalAccountException e) {
        log.info("用户{}账户不存在，抢红包发起失败", e.getUserId());
        return RedPacketResult.error("账户不存在");
    }

    @ExceptionHandler(Throwable.class)
    public RedPacketResult<Object> unhandled() {
        return RedPacketResult.error("未知错误");
    }
}
