package com.practice.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateTimeUtil {
    public static String millis2DateTime(DateTimeFormatter dateTimeFormatter, long timestamp) {
        return dateTimeFormatter.format(Instant
                                            .ofEpochMilli(timestamp)
                                            .atZone(ZoneId.systemDefault()));
    }
}
