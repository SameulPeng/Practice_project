package com.practice.common.pojo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 红包基本信息封装类，包括发起用户ID、红包总金额、红包份数、红包有效期
 */
@Getter
@Setter
@ToString
public class RedPacketInfo {
    /**
     * 发起用户ID
     */
    private String userId;
    /**
     * 红包总金额
     */
    private int amount;
    /**
     * 红包份数
     */
    private int shareNum;
    /**
     * 红包有效期
     */
    private int expireTime;
}
