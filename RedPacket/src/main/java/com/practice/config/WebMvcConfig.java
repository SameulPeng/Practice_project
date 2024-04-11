package com.practice.config;

import com.practice.interceptor.LoginInterceptor;
import com.practice.interceptor.ShareInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Mvc配置类
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private ShareInterceptor shareInterceptor;

    @Autowired
    private void setShareInterceptor(ShareInterceptor shareInterceptor) {
        this.shareInterceptor = shareInterceptor;
    }

    /**
     * 注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 超过红包的访问时限的访问请求将被拒绝
        registry.addInterceptor(shareInterceptor).addPathPatterns("/redpacket/share");
        // 未登录的访问请求将被拒绝
//        registry.addInterceptor(new LoginInterceptor()).addPathPatterns("/redpacket/publish", "/redpacket/share");
    }
}
