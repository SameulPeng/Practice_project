package com.practice.util;

/**
 * 红包Key字符串处理工具类
 */
public class RedPacketKeyUtil {
    /**
     * 生成红包key
     * @param serviceId JVM编号
     * @param threadId 线程ID
     * @param userId 发起用户ID
     * @param amount 红包总金额
     * @param timestamp 当前毫秒时间戳
     * @return 红包key
     */
    public static String generateKey(String serviceId, long threadId, String userId, int amount, long timestamp) {
        return serviceId + threadId + '-' + userId + '-' + amount + '-' + timestamp;
    }

    /**
     * 从红包key解析发起时间
     * @param key 红包key
     * @return 红包发起的毫秒时间戳
     */
    public static long parseTimestamp(String key) {
        return Long.parseLong(key.substring(key.lastIndexOf('-') + 1));
    }

    /**
     * 从红包key解析发起用户ID和红包总金额
     * @param key 红包key
     * @return 发起用户ID和红包总金额
     */
    public static String getUserIdAndAmount(String key) {
        return key.substring(key.indexOf('-') + 1, key.lastIndexOf('-'));
    }

    /**
     * 从发起用户ID和红包总金额分离发起用户ID
     * @param userIdAndAmount 发起用户ID和红包总金额
     * @return 发起用户ID
     */
    public static String parseUserId(String userIdAndAmount) {
        return userIdAndAmount.substring(0, userIdAndAmount.indexOf('-'));
    }

    /**
     * 从发起用户ID和红包总金额
     * @param userIdAndAmount 发起用户ID和红包总金额
     * @return 红包总金额
     */
    public static int parseAmount(String userIdAndAmount) {
        return Integer.parseInt(userIdAndAmount.substring(userIdAndAmount.indexOf('-') + 1));
    }
}
