package com.practice.config;

import com.practice.interceptor.ShareInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * Mvc配置类
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    /**
     * 注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 设置红包的访问时限为3小时，对距当前时间超过3小时的红包的访问请求将被拒绝
        registry.addInterceptor(new ShareInterceptor(3, TimeUnit.HOURS));
    }
}
