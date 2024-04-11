package com.practice.common.pojo;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SortedShareInfo extends ShareInfo {
    private int shareRank;
    private int timeCostRank;

    public SortedShareInfo(ShareInfo shareInfo, int shareRank, int timeCostRank) {
        super(shareInfo.getShare(), shareInfo.getTimeCost());
        this.shareRank = shareRank;
        this.timeCostRank = timeCostRank;
    }
}
