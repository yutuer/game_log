package com.gamelog.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * Web MVC 配置
 * 注册 HTTP 日志拦截器
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private HttpLoggingInterceptor httpLoggingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(httpLoggingInterceptor)
                .addPathPatterns("/api/**");
    }

    /**
     * 注册请求/响应包装 Filter，使拦截器可以读取 body
     */
    @org.springframework.context.annotation.Bean
    public OncePerRequestFilter contentCachingFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {
                ContentCachingRequestWrapper requestWrapper =
                        new ContentCachingRequestWrapper(request);
                ContentCachingResponseWrapper responseWrapper =
                        new ContentCachingResponseWrapper(response);
                try {
                    filterChain.doFilter(requestWrapper, responseWrapper);
                } finally {
                    // 必须调用 copyBodyToResponse，否则响应体无法返回给客户端
                    responseWrapper.copyBodyToResponse();
                }
            }
        };
    }
}
