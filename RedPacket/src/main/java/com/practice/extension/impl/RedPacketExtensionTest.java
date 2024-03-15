package com.practice.extension.impl;

import com.practice.common.result.RedPacketResult;
import com.practice.common.result.ShareResult;
import com.practice.extension.RedPacketExtension;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
//@Component
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
    public RedPacketResult<ShareResult> afterShare(String key, String userId, RedPacketResult<ShareResult> result) {
        log.info("参与抢红包后的扩展方法");
        return result;
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
