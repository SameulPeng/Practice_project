package com.practice.interceptor;

import com.practice.util.RedPacketKeyUtil;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * 参与抢红包拦截器，拦截对已经过期一段时间的红包的访问
 */
@Slf4j
public class ShareInterceptor implements HandlerInterceptor {
    private final long millis; // 红包的访问时限

    public ShareInterceptor(long time, TimeUnit timeUnit) {
        this.millis = timeUnit.toMillis(time);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 拦截参与抢红包请求
        if (request.getRequestURI().equals("/redpacket/share")) {
            // 计算红包访问时限毫秒时间戳
            long before = System.currentTimeMillis() - millis;
            // 对访问时限以内的红包的查询可以放行，否则拒绝
            String key = request.getParameter("key");
            if (RedPacketKeyUtil.parseTimestamp(key) >= before) {
                return true;
            } else {
                log.info("拒绝对红包{}的请求", key);
                return false;
            }
        }
        return true;
    }
}
