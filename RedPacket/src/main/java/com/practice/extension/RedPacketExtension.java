package com.practice.extension;

import com.practice.common.result.RedPacketResult;

import java.util.Map;

/**
 * 抢红包业务扩展接口，将此接口的实现类注册到容器后，将在指定阶段执行指定方法
 */
public interface RedPacketExtension {
    /**
     * 发起抢红包前<br/>
     * 如果不是必要逻辑，建议进行异常捕获
     * @param userId 发起抢红包用户ID
     * @param amount 红包总金额，单位为分
     * @param shareNum 拆分小红包份数
     * @param expireTime 红包过期时长，单位为秒
     */
    default void beforePublish(String userId, int amount, int shareNum, int expireTime) {}

    /**
     * 发起抢红包后<br/>
     * 在组合类中进行了异常捕获
     * @param key 红包key
     * @param userId 发起抢红包用户ID
     * @param amount 红包总金额，单位为分
     * @param shareNum 拆分小红包份数
     * @param expireTime 红包过期时长，单位为秒
     */
    default void afterPublish(String key, String userId, int amount, int shareNum, int expireTime) {}

    /**
     * 参与抢红包前<br/>
     * 如果不是必要逻辑，建议进行异常捕获
     * @param key 红包key
     * @param userId 抢红包用户ID
     */
    default void beforeShare(String key, String userId) {}

    /**
     * 抢红包结果写入缓存前<br/>
     * 在组合类中进行了异常捕获
     * @param mapResult 抢红包结果
     * @return 处理后的抢红包结果
     */
    default Map<String, Object> onCache(Map<String, Object> mapResult) {
        return mapResult;
    }

    /**
     * 参与抢红包后<br/>
     * 在组合类中进行了异常捕获
     * @param key 红包key
     * @param userId 抢红包用户ID
     * @param redPacketResult 抢红包结果
     * @return 处理后的抢红包结果
     */
    @SuppressWarnings("rawtypes")
    default RedPacketResult afterShare(String key, String userId, RedPacketResult redPacketResult) {
        return redPacketResult;
    }

    /**
     * 红包结算后，具有幂等性<br/>
     * 不包含在结算的事务中，因此不保证一致性<br/>
     * 如果不是必要逻辑，建议进行异常捕获
     * @param key 红包key
     */
    default void afterSettlementIdempotent(String key) {}

    /**
     * 红包结算后<br/>
     * 如果不是必要逻辑，建议进行异常捕获
     * @param key 红包key
     */
    default void afterSettlement(String key) {}

    /**
     * 红包结束且未抢完时<br/>
     * 依赖Redis的key过期事件监听功能<br/>
     * 如果不是必要逻辑，建议进行异常捕获
     * @param key 红包key
     */
    default void onExpire(String key) {}

    /**
     * 红包结果移除时<br/>
     * 依赖Redis的key过期事件监听功能<br/>
     * 如果不是必要逻辑，建议进行异常捕获
     * @param key 红包key
     */
    default void onRemove(String key) {}
}
