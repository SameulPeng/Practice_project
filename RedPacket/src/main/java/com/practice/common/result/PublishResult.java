package com.practice.common.result;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 发起抢红包统一响应结果类
 */
@Getter
@Setter
@ToString
public class PublishResult {
    private final String dateTime;
    private final int amount;
    private final int shareNum;
    private final long expireTime;

    public static PublishResult publishSuccess(String dateTime, int amount, int shareNum, long expireTime) {
        return new PublishResult(dateTime, amount, shareNum, expireTime);
    }

    public PublishResult(String dateTime, int amount, int shareNum, long expireTime) {
        this.dateTime = dateTime;
        this.amount = amount;
        this.shareNum = shareNum;
        this.expireTime = expireTime;
    }
}
