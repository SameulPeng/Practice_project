package com.practice.extension.impl;

import com.practice.common.result.RedPacketResult;
import com.practice.common.result.ShareResult;
import com.practice.extension.RedPacketExtension;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 抢红包业务扩展测试类
 */
@Slf4j
@Component
public class RedPacketExtensionTest implements RedPacketExtension {
    @Override
    public void beforePublish(String userId, int amount, int shareNum, int expireTime) {
        log.info("发起抢红包前的扩展方法");
    }

    @Override
    public void afterPublish(String userId, int amount, int shareNum, int expireTime) {
        log.info("发起抢红包后的扩展方法");
    }

    @Override
    public void beforeShare(String key, String userId) {
        log.info("参与抢红包前的扩展方法");
    }

    @Override
    public Map<String, String> onCache(Map<String, String> mapResult) {
        log.info("抢红包结果写入缓存前的扩展方法");
        return mapResult;
    }

    @Override
    public RedPacketResult<ShareResult> afterShare(String key, String userId, RedPacketResult<ShareResult> result) {
        log.info("参与抢红包后的扩展方法");
        return result;
    }

    @Override
    public void afterSettlementIdempotent(String key) {
        log.info("红包结算后具有幂等性的扩展方法");
    }

    @Override
    public void afterSettlement(String key) {
        log.info("红包结算后的扩展方法");
    }

    @Override
    public void onExpire(String key) {
        log.info("红包结束且未抢完时的扩展方法");
    }

    @Override
    public void onRemove(String key) {
        log.info("红包结果移除时的扩展方法");
    }
}
