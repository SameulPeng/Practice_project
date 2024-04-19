package com.practice.common.util;

import com.practice.common.exception.IllegalPropertyException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

/**
 * JWT令牌工具类
 */
public class JwtUtil {
    /**
     * Base64加密密钥
     */
    private static final Key BASE64_ENCODED_SECRET_KEY;
    /**
     * 有效期，单位为毫秒
     */
    private static final long EXPIRE_TIME;

    static {
        String secretKeySource;
        Integer expireTime;
        Properties params = new Properties();

        // 读取 jwt.properties 配置文件
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                ViableConfigUtil.get("jwt.properties")))) {
            params.load(br);

            secretKeySource = (String) params.get("jwt.secret-key-source");
            expireTime = Integer.parseInt((String) params.get("jwt.expire-time"));

            if (secretKeySource.getBytes(StandardCharsets.UTF_8).length < 24) {
                throw new IllegalPropertyException("JWT密钥原始字符串长度不应低于24字节：jwt.secret-key-source");
            }
            if (expireTime < -1 || expireTime > 365 * 24 * 60 * 60) {
                throw new IllegalPropertyException("JWT令牌有效期设置有误：jwt.expire-time");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        BASE64_ENCODED_SECRET_KEY = new SecretKeySpec(
                Base64.getEncoder().encode(secretKeySource.getBytes(StandardCharsets.UTF_8)),
                "HmacSHA256"
        );

        EXPIRE_TIME = expireTime * 1000L;
    }

    /**
     * 生成JWT令牌，包含键值对数据
     * @param claims 键值对数据
     * @return JWT令牌字符串
     */
    public static String generate(Map<String, Object> claims) {
        JwtBuilder jwtBuilder = Jwts.builder()
                // 添加键值对数据
                .addClaims(claims)
                // 使用HS256算法和指定密钥进行加密
                .signWith(BASE64_ENCODED_SECRET_KEY, SignatureAlgorithm.HS256);
        // 设置过期时间
        if (EXPIRE_TIME > 0) jwtBuilder.setExpiration(new Date(System.currentTimeMillis() + EXPIRE_TIME));
        return jwtBuilder.compact();
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
