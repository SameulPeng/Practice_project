package com.practice.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.common.result.RedPacketResult;
import com.practice.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;

/**
 * 登录校验拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 拦截发起抢红包和参与抢红包请求，获取JWT令牌
        String jwt = request.getHeader("Token");
        boolean proceed = false;
        // 如果没有JWT令牌，则拒绝访问
        if (jwt != null && jwt.length() > 0) {
            try {
                // 解析JWT令牌字符串
                Claims claims = JwtUtil.parse(jwt);
                Date expiration = claims.getExpiration();
                // 如果没有设置过期时间，或过期时间晚于当前时间，则可以放行，否则拒绝访问
                if (expiration == null || expiration.getTime() > System.currentTimeMillis()) {
                    // 从JWT令牌中获取用户ID
                    String userId = (String) claims.get("userId");
                    // 将用户ID放入请求域
                    request.setAttribute("userId", userId);
                    proceed = true;
                }
            } catch (Exception ignore) {}
        }
        // 如果拒绝访问，则直接进行未登录响应
        if (!proceed) {
            // 设置响应头信息的内容类型和字符集
            response.setContentType("application/json;charset=UTF-8");
            // 进行未登录的响应，转换为JSON格式字符串写出
            new ObjectMapper().writeValue(response.getWriter(), RedPacketResult.notLogin());
        }
        return proceed;
    }
}
