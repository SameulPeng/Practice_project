package com.practice.common.result;

import lombok.Getter;

@Getter
public class LoginResult {
    /**
     * 响应标识<br/>
     * 0表示登录成功，1表示登录失败，2表示登出成功
     */
    private final int status;
    /**
     * 信息
     */
    private final String msg;
    /**
     * JWT令牌字符串<br/>
     * 如果登录成功则附带此项
     */
    private final String jwt;

    public LoginResult(int status, String msg, String jwt) {
        this.status = status;
        this.msg = msg;
        this.jwt = jwt;
    }

    /**
     * 登录成功
     */
    public static LoginResult loginSuccess(String jwt) {
        return new LoginResult(0, "登录成功", jwt);
    }

    /**
     * 登录失败
     */
    public static LoginResult loginFail() {
        return new LoginResult(1, "登录失败", null);
    }

    /**
     * 登出
     */
    public static LoginResult logout() {
        return new LoginResult(2, "登出成功", null);
    }
}
