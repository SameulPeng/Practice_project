package com.practice.common.result;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;

/**
 * 参与抢红包统一响应结果类
 */
@Getter
@Setter
@ToString
public class ShareResult {
    // 抢红包标识，1表示抢到红包，0表示抢不到红包，-1表示已经抢到过红包但重复参与
    private int status;
    // 抢红包结果提示信息，如果抢到红包将返回金额信息
    private String msg;
    // 红包结果
    private final Map<String, String> result;
    // 抢到红包的耗时
    private final long timeCost;

    public static ShareResult shareSuccess(String msg, Map<String, String> result, long timeCost) {
        return new ShareResult(1, msg, result, timeCost);
    }

    public static ShareResult shareFail(String msg, Map<String, String> result) {
        return new ShareResult(0, msg, result, 0L);
    }

    public static ShareResult shareRedo(String msg) {
        return new ShareResult(-1, msg, null, 0L);
    }

    public ShareResult(int status, String msg, Map<String, String> result, long timeCost) {
        this.status = status;
        this.msg = msg;
        this.result = result;
        this.timeCost = timeCost;
    }
}
