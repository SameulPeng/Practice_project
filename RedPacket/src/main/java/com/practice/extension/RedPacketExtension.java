package com.practice.extension;

import com.practice.common.result.RedPacketResult;
import com.practice.common.result.ShareResult;

import java.util.Map;

/**
 * 抢红包业务扩展接口，将此接口的实现类注册到容器后，将在指定阶段执行指定方法
 */
public interface RedPacketExtension {
    /**
     * 发起抢红包前
     * @param userId 发起抢红包用户ID
     * @param amount 红包总金额，单位为分
     * @param shareNum 拆分小红包份数
     * @param expireTime 红包过期时长，单位为秒
     */
    default void beforePublish(String userId, int amount, int shareNum, int expireTime) {}

    /**
     * 发起抢红包后
     * @param userId 发起抢红包用户ID
     * @param amount 红包总金额，单位为分
     * @param shareNum 拆分小红包份数
     * @param expireTime 红包过期时长，单位为秒
     */
    default void afterPublish(String userId, int amount, int shareNum, int expireTime) {}

    /**
     * 参与抢红包前
     * @param key 红包key
     * @param userId 抢红包用户ID
     */
    default void beforeShare(String key, String userId) {}

    /**
     * 抢红包结果写入缓存前
     * @param mapResult 抢红包结果
     * @return 处理后的抢红包结果
     */
    default Map<String, String> onCache(Map<String, String> mapResult) {
        return mapResult;
    }

    /**
     * 参与抢红包后
     * @param key 红包key
     * @param userId 抢红包用户ID
     * @param result 抢红包结果
     * @return 处理后的抢红包结果
     */
    default RedPacketResult<ShareResult> afterShare(String key, String userId, RedPacketResult<ShareResult> result) {
        return result;
    }

    /**
     * 红包结算后，具有幂等性
     * 不包含在结算的事务中，因此不保证一致性
     * @param key 红包key
     */
    default void afterSettlementIdempotent(String key) {}

    /**
     * 红包结算后
     * @param key 红包key
     */
    default void afterSettlement(String key) {}

    /**
     * 红包结束且未抢完时
     * 依赖Redis的key过期事件监听功能
     * @param key 红包key
     */
    default void onExpire(String key) {}

    /**
     * 红包结果移除时
     * 依赖Redis的key过期事件监听功能
     * @param key 红包key
     */
    default void onRemove(String key) {}
}
