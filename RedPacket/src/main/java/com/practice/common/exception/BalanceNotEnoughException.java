package com.practice.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发起抢红包的余额不足时抛出的异常
 */
@Getter
@AllArgsConstructor
public class BalanceNotEnoughException extends RuntimeException {
    /**
     * 用户ID
     */
    private final String userId;
    /**
     * 红包总金额
     */
    private final int amount;
}
