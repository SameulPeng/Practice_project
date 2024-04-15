package com.practice.common.util;

import org.apache.commons.codec.digest.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 用户账号明文密码加密工具类
 */
public class PasswordUtil {
    /**
     * 将明文密码转换为密文密码
     * @param password 明文密码
     * @return 密文密码
     */
    public static String encode(String password) {
        // 使用SHA256算法获取密码摘要
        byte[] digest = DigestUtils.getSha256Digest().digest(password.getBytes(StandardCharsets.UTF_8));
        // 对密码摘要进行Base64编码
        byte[] encode = Base64.getEncoder().encode(digest);
        return new String(encode, StandardCharsets.UTF_8);
    }
}
