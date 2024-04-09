package com.practice.common.result;

import lombok.Getter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 红包业务统一响应结果类
 * @param <T> 发起抢红包结果、参与抢红包结果或错误标识
 */
@Getter
public class RedPacketResult<T> {
    private final int status; // 响应标识，0表示响应错误，1表示发起抢红包成功，2表示参与抢红包成功，3表示未登录，4表示登录成功，5表示登录失败，6表示登出
    private final String msg; // 信息，如果发起抢红包成功则封装红包key，如果参与抢红包成功则为空，如果响应错误则封装错误提示信息
    private final T result; // 结果，如果发起抢红包成功或参与抢红包成功则封装具体结果，如果响应错误则封装错误标识，如果登录成功则封装JWT令牌

    /**
     * 发起抢红包成功，结果返回发起日期时间、金额、份数、有效期等信息
     */
    public static RedPacketResult<PublishResult> publishSuccess(String key, PublishResult result) {
        // 红包key中可能存在URL特殊字符，如"+"，需要进行URL编码
        key = URLEncoder.encode(key, StandardCharsets.UTF_8);
        return new RedPacketResult<>(1, key, result);
    }

    /**
     * 参与抢红包成功，结果返回金额、耗时、红包结果等信息
     */
    public static RedPacketResult<ShareResult> shareSuccess(ShareResult result) {
        return new RedPacketResult<>(2, null, result);
    }

    /**
     * 响应错误
     */
    public static RedPacketResult<Integer> error(ErrorType type) {
        return new RedPacketResult<>(0, type.message, type.code);
    }

    /**
     * 未登录
     */
    public static RedPacketResult<String> notLogin() {
        return new RedPacketResult<>(3, "未登录", null);
    }

    /**
     * 登录成功
     */
    public static RedPacketResult<String> loginSuccess(String jwt) {
        return new RedPacketResult<>(4, "登录成功", jwt);
    }

    /**
     * 登录失败
     */
    public static RedPacketResult<String> loginFail() {
        return new RedPacketResult<>(5, "登录失败", null);
    }

    /**
     * 登出
     */
    public static RedPacketResult<String> logout() {
        return new RedPacketResult<>(6, "登出成功", null);
    }

    public RedPacketResult(int status, String msg, T result) {
        this.status = status;
        this.msg = msg;
        this.result = result;
    }

    /**
     * 错误类型
     */
    public enum ErrorType {
        /**
         * 未知错误
         */
        UNKNOWN_ERROR("未知错误", 0),
        /**
         * 余额不足
         */
        BALANCE_NOT_ENOUGH("余额不足，抢红包发起失败", 1),
        /**
         * 账户异常
         */
        ILLEGAL_ACCOUNT("账户不存在，抢红包发起失败", 2),
        /**
         * 总金额设置超出范围
         */
        WRONG_AMOUNT("总金额设置超出范围，抢红包发起失败", 3),
        /**
         * 份数设置超出范围
         */
        WRONG_SHARE_NUM("份数设置超出范围，抢红包发起失败", 4),
        /**
         * 有效期设置超出范围
         */
        WRONG_EXPIRE_TIME("有效期设置超出范围，抢红包发起失败", 5),
        /**
         * 参与抢红包无法获得有效结果
         */
        SHARE_ERROR("没有找到红包，可能是网络异常或已经结束", 6),
        /**
         * 用户不匹配
         */
        USER_MISMATCH("发起抢红包或参与抢红包的用户与当前登录用户不匹配", 7);

        private final String message;
        private final Integer code;

        ErrorType(String message, Integer code) {
            this.message = message;
            this.code = code;
        }
    }
}
