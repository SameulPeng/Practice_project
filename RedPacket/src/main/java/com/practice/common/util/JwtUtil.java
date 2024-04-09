package com.practice.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * JWT令牌工具类
 */
public class JwtUtil {
    private static final Key BASE64_ENCODED_SECRET_KEY = // Base64加密密钥
            new SecretKeySpec(
                    Base64.getEncoder().encode("RedPacket-base64-encoded-secret-key".getBytes(StandardCharsets.UTF_8)),
                    "HmacSHA256"
            );
    private static final long EXPIRE_TIME = 60 * 60 * 1000L; // 有效期，单位为毫秒

    /**
     * 生成JWT令牌，包含键值对数据
     * @param claims 键值对数据
     * @return JWT令牌字符串
     */
    public static String generate(Map<String, Object> claims) {
        return Jwts.builder()
                // 添加键值对数据
                .addClaims(claims)
                // 使用HS256算法和指定密钥进行加密
                .signWith(BASE64_ENCODED_SECRET_KEY, SignatureAlgorithm.HS256)
                // 设置过期时间
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
                .compact();
    }

    /**
     * 解析JWT令牌，获取负载内容
     * @param jwt JWT令牌字符串
     * @return JWT令牌负载内容
     */
    public static Claims parse(String jwt) {
        return Jwts.parserBuilder()
                // 使用指定密钥进行解密
                .setSigningKey(BASE64_ENCODED_SECRET_KEY)
                .build()
                // 解析JWS字符串
                .parseClaimsJws(jwt)
                // 获取负载内容
                .getBody();
    }
}
