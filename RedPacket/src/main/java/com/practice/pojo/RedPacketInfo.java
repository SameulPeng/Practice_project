package com.practice.pojo;

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
    private String userId;
    private int amount;
    private int shareNum;
    private int expireTime;
}
