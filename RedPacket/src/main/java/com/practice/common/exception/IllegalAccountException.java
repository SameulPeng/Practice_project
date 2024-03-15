package com.practice.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发起抢红包的账户不存在时抛出的异常
 */
@Getter
@AllArgsConstructor
public class IllegalAccountException extends RuntimeException {
    private final String userId;
}
