package com.practice.extension;

import com.practice.common.result.RedPacketResult;
import com.practice.common.result.ShareResult;

/**
 * 抢红包业务扩展接口，将此接口的实现类注册到容器后，将在指定阶段执行指定方法
 */
public interface RedPacketExtension {
    /**
     * 发起抢红包前
     */
    default void beforePublish(String userId, int amount, int shareNum, int expireTime) {}

    /**
     * 发起抢红包后
     */
    default void afterPublish(String userId, int amount, int shareNum, int expireTime) {}

    /**
     * 参与抢红包前
     */
    default void beforeShare(String key, String userId) {}

    /**
     * 参与抢红包后
     */
    default RedPacketResult<ShareResult> afterShare(String key, String userId, RedPacketResult<ShareResult> result) {
        return result;
    }

    /**
     * 红包结束且未抢完时
     */
    default void onExpire(String key) {}

    /**
     * 红包结果移除时
     */
    default void onRemove(String key) {}
}
