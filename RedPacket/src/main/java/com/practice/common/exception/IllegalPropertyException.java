package com.practice.common.exception;

/**
 * 读取到不合法的配置参数时抛出的异常
 */
public class IllegalPropertyException extends Exception {
    public IllegalPropertyException(String message) {
        super(message);
    }
}
