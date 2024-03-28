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
    private final int status; // 响应标识，1表示发起抢红包成功，2表示参与抢红包成功，其他数值表示响应错误
    private final String msg; // 响应错误信息
    private final T result; // 结果

    /**
     * 发起抢红包成功，结果返回发起日期时间、金额、份数、有效期等信息
     */
    public static <E> RedPacketResult<E> publishSuccess(String msg, E result) {
        return new RedPacketResult<>(1, msg, result);
    }

    /**
     * 参与抢红包成功，结果返回金额、用时、红包结果等信息
     */
    public static <E> RedPacketResult<E> shareSuccess(E result) {
        return new RedPacketResult<>(2, null, result);
    }

    /**
     * 响应错误
     */
    public static <E> RedPacketResult<E> error(String msg) {
        return new RedPacketResult<>(0, msg, null);
    }

    public RedPacketResult(int status, String msg, T result) {
        this.status = status;
        this.msg = msg;
        this.result = result;
    }
}
