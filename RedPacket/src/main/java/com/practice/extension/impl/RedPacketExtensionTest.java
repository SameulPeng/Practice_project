package com.practice.extension.impl;

import com.practice.common.result.RedPacketResult;
import com.practice.extension.RedPacketExtension;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 抢红包业务扩展测试类
 */
@Slf4j
@Component
@ConditionalOnProperty("red-packet.extension-test")
public class RedPacketExtensionTest implements RedPacketExtension {
    @Override
    public void beforePublish(String userId, int amount, int shareNum, int expireTime) {
        log.debug("发起抢红包前的扩展方法");
    }

    @Override
    public void afterPublish(String key, String userId, int amount, int shareNum, int expireTime) {
        log.debug("发起抢红包后的扩展方法");
    }

    @Override
    public void beforeShare(String key, String userId) {
        log.debug("参与抢红包前的扩展方法");
    }

    @Override
    public Map<String, Object> onCache(Map<String, Object> mapResult) {
        log.debug("抢红包结果写入缓存前的扩展方法");
        return mapResult;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public RedPacketResult afterShare(String key, String userId, RedPacketResult redPacketResult) {
        log.debug("参与抢红包后的扩展方法");
        return redPacketResult;
    }

    @Override
    public void afterSettlementIdempotent(String key) {
        log.debug("红包结算后具有幂等性的扩展方法");
    }

    @Override
    public void afterSettlement(String key) {
        log.debug("红包结算后的扩展方法");
    }

    @Override
    public void onExpire(String key) {
        log.debug("红包结束且未抢完时的扩展方法");
    }

    @Override
    public void onRemove(String key) {
        log.debug("红包结果移除时的扩展方法");
    }
}
