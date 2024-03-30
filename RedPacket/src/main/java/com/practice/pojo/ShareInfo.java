package com.practice.pojo;

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
    private int share; // 抢到的红包金额
    private long timeCost; // 耗时
}
