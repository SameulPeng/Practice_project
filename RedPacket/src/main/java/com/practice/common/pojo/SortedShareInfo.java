package com.practice.common.pojo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 红包结果每个用户项的信息封装类子类，包括抢到的红包金额、耗时以及二者的排名
 */
@Getter
@Setter
@ToString
public class SortedShareInfo extends ShareInfo {
    /**
     * 抢到的红包金额排名<br/>
     * 金额越大，排名越高，-1表示未进入排名，非负整数表示排名（第一名为0，以此类推）
     */
    private int shareRank = -1;
    /**
     * 抢到红包的耗时排名<br/>
     * 耗时越短，排名越高，-1表示未进入排名，非负整数表示排名（第一名为0，以此类推）
     */
    private int timeCostRank = -1;

    public SortedShareInfo(ShareInfo shareInfo) {
        super(shareInfo.getShare(), shareInfo.getTimeCost());
    }
}
