package com.practice.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具类
 */
public class DateTimeUtil {
    /**
     * 将Unix毫秒时间戳转换为指定格式的日期时间字符串
     * @param dateTimeFormatter 日期时间格式化器
     * @param timestamp Unix毫秒时间戳
     * @return 格式化后的日期时间字符串
     */
    public static String millis2DateTime(DateTimeFormatter dateTimeFormatter, long timestamp) {
        return dateTimeFormatter.format(Instant
                                            .ofEpochMilli(timestamp)
                                            .atZone(ZoneId.systemDefault()));
    }
}
