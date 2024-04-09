package com.practice.common.util;

/**
 * 红包key字符串处理工具类
 */
public class RedPacketKeyUtil {
    private final static int SERVICE_ID_CHARS = 2; // JVM编号使用两个字节，不重复范围为0到4095
    private final static int THREAD_ID_CHARS = 2; // 线程ID使用两个字节，不重复范围为0到4095
    private final static int AMOUNT_CHARS = 5; // 红包总金额使用5个字节，上限为1073741823，约一千万元
    private final static int EXPIRE_TIME_CHARS = 4; // 有效期使用4个字节，上限为16777215，约194天
    private final static int TIMESTAMP_CHARS = 7; // 时间戳使用7个字节，上限为4398046511103，约139年（从1970年1月1日0时0分0秒起）

    /**
     * 生成红包key
     * @param serviceId JVM编号
     * @param threadId  线程ID
     * @param amount    红包总金额
     * @param expireTime 红包有效期
     * @param timestamp 发起毫秒时间戳
     * @param userId    发起用户ID
     * @return 红包key
     */
    public static String generateKey(int serviceId, long threadId, int amount, int expireTime, long timestamp, String userId) {
        String s1 = i2chars(serviceId, SERVICE_ID_CHARS);
        String s2 = i2chars(threadId, THREAD_ID_CHARS);
        String s3 = i2chars(amount, AMOUNT_CHARS);
        String s4 = i2chars(expireTime, EXPIRE_TIME_CHARS);
        String s5 = i2chars(timestamp, TIMESTAMP_CHARS);

        // 格式为： JVM编号 + 线程ID + 红包总金额 + 红包有效期 + 红包发起毫秒时间戳 + 发起用户ID
        return s1 + s2 + s3 + s4 + s5 + userId;
    }

    /**
     * 从红包key解析红包总金额
     * @param key 红包key
     * @return 红包总金额
     */
    public static int parseAmount(String key) {
        return (int) chars2i(key.substring(
                SERVICE_ID_CHARS + THREAD_ID_CHARS,
                SERVICE_ID_CHARS + THREAD_ID_CHARS + AMOUNT_CHARS
        ));
    }

    /**
     * 从红包key解析红包有效期
     * @param key 红包key
     * @return 红包有效期
     */
    public static int parseExpireTime(String key) {
        return (int) chars2i(key.substring(
                SERVICE_ID_CHARS + THREAD_ID_CHARS + AMOUNT_CHARS,
                SERVICE_ID_CHARS + THREAD_ID_CHARS + AMOUNT_CHARS + EXPIRE_TIME_CHARS
        ));
    }

    /**
     * 从红包key解析发起时间
     * @param key 红包key
     * @return 红包发起的毫秒时间戳
     */
    public static long parseTimestamp(String key) {
        return chars2i(key.substring(
                SERVICE_ID_CHARS + THREAD_ID_CHARS + AMOUNT_CHARS + EXPIRE_TIME_CHARS,
                SERVICE_ID_CHARS + THREAD_ID_CHARS + AMOUNT_CHARS + EXPIRE_TIME_CHARS + TIMESTAMP_CHARS
        ));
    }

    /**
     * 从红包key解析发起用户ID
     * @param key 红包key
     * @return 发起用户ID
     */
    public static String parseUserId(String key) {
        return key.substring(SERVICE_ID_CHARS + THREAD_ID_CHARS + AMOUNT_CHARS + EXPIRE_TIME_CHARS + TIMESTAMP_CHARS);
    }

    /**
     * 对进行抢红包耗时进行编码
     * @param timeCost 抢红包耗时
     * @return 抢红包耗时的编码结果
     */
    public static String encodeTimeCost(long timeCost) {
        return i2chars(timeCost, EXPIRE_TIME_CHARS);
    }

    /**
     * 对进行抢红包耗时进行解码
     * @param encodedTimeCost 编码后的抢红包耗时
     * @return 抢红包耗时
     */
    public static long decodeTimeCost(String encodedTimeCost) {
        return chars2i(encodedTimeCost);
    }

    /**
     * 将待映射整型数的低位转换为目标数量的字符串<br/>
     * 每6位（0到63）按顺序映射到 0-9、A-Z、a-z、*、+
     * @param charNums 目标字符数
     * @param i 待映射整型数
     * @return 目标字符串
     */
    private static String i2chars(long i, int charNums) {
        char[] chars = new char[charNums];
        for (int k = 0; k < charNums; k++) {
            // 截取整型数i二进制表示的第(k + 1)个低6位
            long j = i >>> ((k << 1) + (k << 2)) & 0x3F;
            if (j < 10) {
                // 将0到9映射到 0-9
                j += 48;
            } else if (j < 36) {
                // 将10到35映射到 A-Z
                j += 55;
            } else if (j < 62) {
                // 将36到61映射到 a-z
                j += 61;
            } else {
                // 将62、63映射到 *、+
                j -= 20;
            }
            chars[charNums - k - 1] = (char) j;
        }
        return new String(chars);
    }

    /**
     * 将待解析字符串转换为目标整型数<br/>
     * 0-9、A-Z、a-z、*、+ 按顺序反映射为6位（0到63）
     * @param str 待解析字符串
     * @return 目标整型数
     */
    private static long chars2i(String str) {
        char[] chars = str.toCharArray();
        long l = 0L;
        for (int k = 0; k < chars.length; k++) {
            char c = chars[chars.length - k - 1];
            if (c < 0x2C) {
                // 将 *、+ 反映射为62、63
                c += 20;
            } else if (c < 0x3A) {
                // 将 0-9 反映射为0到9
                c -= 48;
            } else if (c < 0x5B) {
                // 将 A-Z 反映射为10到35
                c -= 55;
            } else {
                // 将 a-z 反映射为36到61
                c -= 61;
            }
            // 填充整型数l二进制表示的第(k + 1)个低6位
            l ^= ((long) (c & 0x3F) << ((k << 1) + (k << 2)));
        }
        return l;
    }
}
