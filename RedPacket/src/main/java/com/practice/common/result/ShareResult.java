package com.practice.common.result;

import lombok.Getter;

import java.util.Map;

/**
 * 参与抢红包统一响应结果类
 */
@Getter
public class ShareResult {
    private final int status; // 抢红包标识，1表示抢到红包，0表示抢不到红包，2表示已经抢到过红包但重复参与
    private final String msg; // 抢红包结果提示信息，如果抢到红包将返回金额信息
    private final Map<String, Object> mapResult; // 红包结果
    private final int share; // 抢到的金额
    private final long timeCost; // 抢到红包的耗时

    /**
     * 抢到红包，返回提示信息、金额、红包结果、耗时
     */
    private static ShareResult shareSuccess(String msg, Map<String, Object> result, int share, long timeCost) {
        return new ShareResult(1, msg, result, share, timeCost);
    }

    /**
     * 抢不到红包，返回提示信息、红包结果
     */
    private static ShareResult shareFail(String msg, Map<String, Object> result) {
        return new ShareResult(0, msg, result, 0, 0L);
    }

    /**
     * 已经抢到红包但重复参与，返回提示信息、金额、耗时
     */
    private static ShareResult shareRedo(String msg, int share, long timeCost) {
        return new ShareResult(2, msg, null, share, timeCost);
    }

    public static ShareResult share(ShareType type) {
        return share(type, null, 0, 0L);
    }

    public static ShareResult share(ShareType type, int share, long timeCost) {
        return share(type, null, share, timeCost);
    }

    public static ShareResult share(ShareType type, Map<String, Object> mapResult) {
        return share(type, mapResult, 0, 0L);
    }

    public static ShareResult share(ShareType type, Map<String, Object> mapResult, int share, long timeCost) {
        return switch (type) {
            case SUCCESS_ONGOING ->
                    shareSuccess(String.format("您成功抢到 %.2f 元红包，耗时 %.3f 秒", share / 100f, timeCost / 1000f), null, share, timeCost);
            case SUCCESS_END ->
                    shareSuccess(String.format("抢红包已经结束了，您成功抢到 %.2f 元红包，耗时 %.3f 秒", share / 100f, timeCost / 1000f), mapResult, share, timeCost);
            case FAIL_END ->
                    shareFail("抢红包已经结束了，您没抢到红包", mapResult);
            case FAIL_REDO ->
                    shareRedo(String.format("抢红包还未结束，您已经抢到 %.2f 元红包，耗时 %.3f 秒，请等待结束", share / 100f, timeCost / 1000f), share, timeCost);
            case FAIL_NOT_FOUND ->
                    shareFail("您查看的红包在较早的时候已经结束", null);
        };
    }

    private ShareResult(int status, String msg, Map<String, Object> mapResult, int share, long timeCost) {
        this.status = status;
        this.msg = msg;
        this.mapResult = mapResult;
        this.share = share;
        this.timeCost = timeCost;
    }

    public enum ShareType {
        /**
         * 抢红包未结束，抢到红包
         */
        SUCCESS_ONGOING,
        /**
         * 抢红包已结束，但已经参与过并抢到红包
         */
        SUCCESS_END,
        /**
         * 抢红包已结束，抢不到红包
         */
        FAIL_END,
        /**
         * 抢红包未结束，已经参与过并抢到红包，重复参与
         */
        FAIL_REDO,
        /**
         * 查询过早的红包，结果已经过期
         */
        FAIL_NOT_FOUND
    }
}
