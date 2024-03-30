package com.practice.interceptor;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.common.result.RedPacketResult;
import com.practice.common.result.ShareResult;
import com.practice.config.RedPacketProperties;
import com.practice.util.RedPacketKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 参与抢红包拦截器，拦截对已经过期一段时间的红包的访问
 */
@Slf4j
@Component
public class ShareInterceptor implements HandlerInterceptor {
    @Autowired
    private RedPacketProperties redPacketProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 拦截参与抢红包请求
        String key = request.getParameter("key");
        // 计算红包的访问时限
        long limit = RedPacketKeyUtil.parseTimestamp(key)
                + RedPacketKeyUtil.parseExpireTime(key) * 1000L
                + redPacketProperties.getBiz().getResultKeepTime() * 1000L;
        // 对访问时限以内的红包的查询可以放行，否则拒绝
        if (System.currentTimeMillis() > limit) {
            response.setContentType("application/json;charset=UTF-8");
            // 进行找不到红包的响应，转换为JSON格式字符串写出
            new ObjectMapper().writeValue(
                    response.getWriter(),
                    RedPacketResult.shareSuccess(
                            ShareResult.share(ShareResult.ShareType.FAIL_NOT_FOUND, null, -1, -1L))
            );
            String userId = request.getParameter("userId");
            log.info("用户 {} 查询一个过早的红包结果 {} ", userId, key);
            return false;
        }
        return true;
    }
}
