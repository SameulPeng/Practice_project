package com.practice.common.result;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 红包业务统一响应结果类
 * @param <T> 发起抢红包结果或参与抢红包结果
 */
@Getter
@Setter
@ToString
public class RedPacketResult<T> {
    // 响应标识，1表示发红包成功，2表示参与抢红包成功，其他数值表示响应错误
    private final int status;
    // 响应错误信息
    private final String msg;
    // 结果
    private final T result;

    // 发红包成功，结果返回发布时间、金额、份数、有效期等信息
    public static <T> RedPacketResult<T> publishSuccess(String msg, T result) {
        return new RedPacketResult<>(1, msg, result);
    }

    // 参与抢红包成功，结果返回金额、用时、红包结果等信息
    public static <T> RedPacketResult<T> shareSuccess(T result) {
        return new RedPacketResult<>(2, null, result);
    }

    // 响应错误
    public static <T> RedPacketResult<T> error(String msg) {
        return new RedPacketResult<>(0, msg, null);
    }

    public RedPacketResult(int status, String msg, T result) {
        this.status = status;
        this.msg = msg;
        this.result = result;
    }
}
