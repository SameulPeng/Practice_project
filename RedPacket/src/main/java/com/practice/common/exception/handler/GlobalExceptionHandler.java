package com.practice.common.exception.handler;

import com.practice.common.logging.ExtLogger;
import com.practice.common.pojo.BigDataInfo;
import com.practice.common.result.RedPacketResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

/**
 * 全局异常处理类，用于处理未被处理的未知异常
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final ExtLogger log = ExtLogger.create(GlobalExceptionHandler.class); // 日志Logger对象

    /**
     * 处理未知异常
     */
    @ExceptionHandler(Throwable.class)
    @SuppressWarnings("rawtypes")
    public RedPacketResult unhandled(Throwable e, HttpServletRequest request) {
        // 按照Throwable类的printStackTrace()方法的逻辑拼接异常栈信息字符串
        StringBuilder sb = new StringBuilder(System.lineSeparator()).append(e);
        for (StackTraceElement traceElement : e.getStackTrace()) {
            sb.append(System.lineSeparator()).append("\tat ").append(traceElement);
        }
        log.error("未处理异常：{}", sb);
        // 使用惰性日志
        log.bigdata("{}", () -> BigDataInfo.of(
                        BigDataInfo.Status.ERROR, null, (String) request.getAttribute("userId"),
                        null, null, null, BigDataInfo.ErrorType.UNKNOWN_ERROR
                ).encode()
        );
        return RedPacketResult.error(RedPacketResult.ErrorType.UNKNOWN_ERROR);
    }
}
