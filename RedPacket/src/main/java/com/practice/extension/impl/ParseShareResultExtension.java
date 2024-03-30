package com.practice.extension.impl;

import com.practice.extension.RedPacketExtension;
import com.practice.pojo.ShareInfo;
import com.practice.util.RedPacketKeyUtil;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 写入缓存前，将红包结果中的原始信息进行解析封装<br></br>
 * 原始信息包括每个用户抢到的红包金额和耗时
 */
@Component
public class ParseShareResultExtension implements RedPacketExtension {
    @Override
    public Map<String, Object> onCache(Map<String, Object> mapResult) {
        Set<Map.Entry<String, Object>> entries = mapResult.entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            String shareAndTimeCost = (String) entry.getValue();
            int idx = shareAndTimeCost.indexOf('-');
            // 将红包结果中的每个用户抢到的红包金额和耗时进行解析封装
            mapResult.put(entry.getKey(),
                    new ShareInfo(Integer.parseInt(shareAndTimeCost.substring(0, idx)),
                            RedPacketKeyUtil.decodeTimeCost(shareAndTimeCost.substring(idx + 1))));
        }
        return RedPacketExtension.super.onCache(mapResult);
    }
}
