package com.practice.common.pojo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * 红包结果每个用户项的信息封装类，包括抢到的红包金额、耗时
 */
@Getter
@ToString
@AllArgsConstructor
public class ShareInfo {
    /**
     * 抢到的红包金额
     */
    private int share;
    /**
     * 抢到红包的耗时
     */
    private long timeCost;
}
