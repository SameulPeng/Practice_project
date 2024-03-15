package com.practice.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发起抢红包的余额不足时抛出的异常
 */
@Getter
@AllArgsConstructor
public class BalanceNotEnoughException extends RuntimeException {
    private final String userId;
    private final int amount;
}
