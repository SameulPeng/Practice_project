package com.practice.common.result;

import lombok.Getter;

/**
 * 发起抢红包统一响应结果类
 */
@Getter
public class PublishResult {
    /**
     * 发起抢红包的日期时间
     */
    private final String dateTime;
    /**
     * 发起抢红包的总金额
     */
    private final int amount;
    /**
     * 发起抢红包的份数
     */
    private final int shareNum;
    /**
     * 发起抢红包的有效期
     */
    private final int expireTime;

    /**
     * 发起抢红包成功，返回发起日期时间、金额、份数、有效期
     */
    public static PublishResult publishSuccess(String dateTime, int amount, int shareNum, int expireTime) {
        return new PublishResult(dateTime, amount, shareNum, expireTime);
    }

    private PublishResult(String dateTime, int amount, int shareNum, int expireTime) {
        this.dateTime = dateTime;
        this.amount = amount;
        this.shareNum = shareNum;
        this.expireTime = expireTime;
    }
}
