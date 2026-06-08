package com.gamelog.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * HTTP 请求/响应日志拦截器
 * 记录所有进入 Controller 的请求和响应到独立日志文件
 */
@Component
public class HttpLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("GatewayLogger");
    private static final int MAX_BODY_LENGTH = 2000;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long startTime = System.currentTimeMillis();
        request.setAttribute("startTime", startTime);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            long startTime = (Long) request.getAttribute("startTime");
            long duration = System.currentTimeMillis() - startTime;

            // 请求体（如果有）
            String requestBody = "";
            if (request instanceof ContentCachingRequestWrapper) {
                ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
                byte[] buf = wrapper.getContentAsByteArray();
                if (buf.length > 0) {
                    requestBody = truncate(new String(buf, 0, Math.min(buf.length, MAX_BODY_LENGTH),
                            request.getCharacterEncoding() != null ? request.getCharacterEncoding() : "UTF-8"));
                }
            }

            // 响应体（如果有）
            String responseBody = "";
            if (response instanceof ContentCachingResponseWrapper) {
                ContentCachingResponseWrapper wrapper = (ContentCachingResponseWrapper) response;
                byte[] buf = wrapper.getContentAsByteArray();
                if (buf.length > 0) {
                    responseBody = truncate(new String(buf, 0, Math.min(buf.length, MAX_BODY_LENGTH),
                            response.getCharacterEncoding() != null ? response.getCharacterEncoding() : "UTF-8"));
                }
            }

            String clientIp = getClientIp(request);
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String query = request.getQueryString();
            int status = response.getStatus();

            log.info(">>> {} {}{} | ip={} | status={} | {}ms",
                    method, uri, (query != null ? "?" + query : ""), clientIp, status, duration);

            if (!requestBody.isEmpty()) {
                log.info(">>> REQUEST BODY: {}", requestBody);
            }
            if (!responseBody.isEmpty()) {
                log.info("<<< RESPONSE BODY: {}", responseBody);
            }
        } catch (Exception e) {
            log.warn("记录 HTTP 日志失败: {}", e.getMessage());
        }
    }

    /**
     * 截断过长的内容
     */
    private String truncate(String content) {
        if (content == null) return "";
        if (content.length() > MAX_BODY_LENGTH) {
            return content.substring(0, MAX_BODY_LENGTH) + "...(truncated)";
        }
        return content;
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
