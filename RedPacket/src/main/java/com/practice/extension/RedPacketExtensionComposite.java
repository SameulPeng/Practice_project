package com.practice.extension;

import com.practice.common.result.RedPacketResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 抢红包业务扩展组合类
 */
@Component
public class RedPacketExtensionComposite implements RedPacketExtension {
    @Autowired(required = false)
    private List<RedPacketExtension> extensions; // 依赖注入所有抢红包业务扩展接口实现类组件

    /**
     * 发起抢红包前
     * @param userId 发起抢红包用户ID
     * @param amount 红包总金额，单位为分
     * @param shareNum 拆分小红包份数
     * @param expireTime 红包过期时长，单位为秒
     */
    @Override
    public void beforePublish(String userId, int amount, int shareNum, int expireTime) {
        if (extensions != null) {
            for (RedPacketExtension extension : extensions) {
                extension.beforePublish(userId, amount, shareNum, expireTime);
            }
        }
    }

    /**
     * 发起抢红包后
     * @param userId 发起抢红包用户ID
     * @param amount 红包总金额，单位为分
     * @param shareNum 拆分小红包份数
     * @param expireTime 红包过期时长，单位为秒
     */
    @Override
    public void afterPublish(String userId, int amount, int shareNum, int expireTime) {
        if (extensions != null) {
            for (RedPacketExtension extension : extensions) {
                extension.afterPublish(userId, amount, shareNum, expireTime);
            }
        }
    }

    /**
     * 参与抢红包前
     * @param key 红包key
     * @param userId 抢红包用户ID
     */
    @Override
    public void beforeShare(String key, String userId) {
        if (extensions != null) {
            for (RedPacketExtension extension : extensions) {
                extension.beforeShare(key, userId);
            }
        }
    }

    /**
     * 抢红包结果写入缓存前
     * @param mapResult 抢红包结果
     * @return 处理后的抢红包结果
     */
    @Override
    public Map<String, Object> onCache(Map<String, Object> mapResult) {
        if (extensions != null) {
            for (RedPacketExtension extension : extensions) {
                mapResult = extension.onCache(mapResult);
            }
        }
         return mapResult;
    }

    /**
     * 参与抢红包后
     * @param key 红包key
     * @param userId 抢红包用户ID
     * @param redPacketResult 抢红包结果
     */
    @Override
    @SuppressWarnings("rawtypes")
    public RedPacketResult afterShare(String key, String userId, RedPacketResult redPacketResult) {
        if (extensions != null) {
            for (RedPacketExtension extension : extensions) {
                redPacketResult = extension.afterShare(key, userId, redPacketResult);
            }
        }
        return redPacketResult;
    }

    /**
     * 红包结算后，具有幂等性<br></br>
     * 不包含在结算的事务中，因此不保证一致性
     * @param key 红包key
     */
    @Override
    public void afterSettlementIdempotent(String key) {
        if (extensions != null) {
            for (RedPacketExtension extension : extensions) {
                extension.afterSettlementIdempotent(key);
            }
        }
    }

    /**
     * 红包结算后
     * @param key 红包key
     */
    @Override
    public void afterSettlement(String key) {
        if (extensions != null) {
            for (RedPacketExtension extension : extensions) {
                extension.afterSettlement(key);
            }
        }
    }

    /**
     * 红包结束且未抢完时<br></br>
     * 依赖Redis的key过期事件监听功能
     * @param key 红包key
     */
    @Override
    public void onExpire(String key) {
        if (extensions != null) {
            for (RedPacketExtension extension : extensions) {
                extension.onExpire(key);
            }
        }
    }

    /**
     * 红包结果移除时<br></br>
     * 依赖Redis的key过期事件监听功能
     * @param key 红包key
     */
    @Override
    public void onRemove(String key) {
        if (extensions != null) {
            for (RedPacketExtension extension : extensions) {
                extension.onRemove(key);
            }
        }
    }
}
