package com.gamelog.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Web MVC 配置
 * 注册 HTTP 日志 Filter
 */
@Configuration
public class WebConfig {

    /**
     * 使用 FilterRegistrationBean 显式注册 HTTP 日志 Filter
     * setOrder(Ordered.HIGHEST_PRECEDENCE) 确保最先执行
     */
    @Bean
    public FilterRegistrationBean<ContentCachingFilter> contentCachingFilter() {
        FilterRegistrationBean<ContentCachingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ContentCachingFilter());
        registration.addUrlPatterns("/*");
        registration.setName("contentCachingFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
