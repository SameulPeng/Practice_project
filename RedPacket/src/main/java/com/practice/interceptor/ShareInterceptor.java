package com.practice.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.common.logging.ExtLogger;
import com.practice.common.pojo.BigDataInfo;
import com.practice.common.result.RedPacketResult;
import com.practice.common.result.ShareResult;
import com.practice.common.util.RedPacketKeyUtil;
import com.practice.config.RedPacketProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 参与抢红包拦截器，拦截对非法红包和已经过期一段时间的红包的访问
 */
@Component
public class ShareInterceptor implements HandlerInterceptor {
    private final static ExtLogger log = ExtLogger.create(ShareInterceptor.class); // 日志Logger对象
    private RedPacketProperties redPacketProperties; // 配置参数类

    @Autowired
    private void setRedPacketProperties(RedPacketProperties redPacketProperties) {
        this.redPacketProperties = redPacketProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 拦截参与抢红包请求，获取红包key
        String key = request.getParameter("key");
        long timestamp = System.currentTimeMillis();

        // 通过校验和检查红包key合法性
        boolean proceed = RedPacketKeyUtil.checkKey(key);

        // 如果红包key合法性校验通过，进一步检查访问时限
        if (proceed) {
            // 计算红包的访问时限
            long limit = RedPacketKeyUtil.parseTimestamp(key)
                    + RedPacketKeyUtil.parseExpireTime(key) * 1000L
                    + redPacketProperties.getBiz().getResultKeepTime() * 1000L;
            // 对访问时限以内的红包的查询可以放行，否则拒绝
            proceed = timestamp <= limit;
        }
        // 如果拒绝访问，则直接进行响应
        if (!proceed) {
            // 设置响应头信息的内容类型和字符集
            response.setContentType("application/json;charset=UTF-8");
            // 进行找不到红包的响应，转换为JSON格式字符串写出
            new ObjectMapper().writeValue(
                    response.getWriter(),
                    RedPacketResult.shareSuccess(
                            ShareResult.share(ShareResult.ShareType.FAIL_NOT_FOUND, null, 0, 0L))
            );
            String userId = request.getParameter("userId");
            log.biz("[{}] [用户 {}] 查询一个过早的红包结果", key, userId);
            // 使用惰性日志
            log.bigdata("{}", () -> BigDataInfo.of(
                    BigDataInfo.Status.SHARE, key, userId, null,
                    BigDataInfo.Share.of(BigDataInfo.Share.ShareType.FAIL_NOT_FOUND, 0, 0, timestamp),
                    null, null
                    ).encode()
            );
        }
        return proceed;
    }
}
