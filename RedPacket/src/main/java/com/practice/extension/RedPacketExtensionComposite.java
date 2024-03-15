package com.practice.extension;

import com.practice.common.result.RedPacketResult;
import com.practice.common.result.ShareResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 抢红包业务扩展组合类
 */
@Component
public class RedPacketExtensionComposite implements RedPacketExtension {
    @Autowired(required = false)
    private List<RedPacketExtension> extensions; // 依赖注入所有抢红包业务扩展接口实现类组件

    /**
     * 发起抢红包前
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
     * 参与抢红包后
     */
    @Override
    public RedPacketResult<ShareResult> afterShare(String key, String userId, RedPacketResult<ShareResult> result) {
        if (extensions != null) {
            for (RedPacketExtension extension : extensions) {
                result = extension.afterShare(key, userId, result);
            }
        }
        return result;
    }

    /**
     * 红包结束且未抢完时
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
     * 红包结果移除时
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
