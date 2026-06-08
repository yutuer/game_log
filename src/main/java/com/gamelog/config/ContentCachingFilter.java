package com.gamelog.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 网关日志 Filter
 * 记录所有进入 Controller 的请求和响应到独立日志文件
 * 直接使用 log4j2 API，不依赖 SLF4J 桥接
 */
public class ContentCachingFilter extends OncePerRequestFilter {

    private static final Logger log = LogManager.getLogger("GatewayLogger");
    private static final int MAX_BODY_LENGTH = 2000;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            writeLog(requestWrapper, responseWrapper, duration);
            // 必须调用，否则客户端拿不到响应
            responseWrapper.copyBodyToResponse();
        }
    }

    private void writeLog(ContentCachingRequestWrapper request,
                          ContentCachingResponseWrapper response,
                          long duration) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String query = request.getQueryString();
            int status = response.getStatus();
            String clientIp = getClientIp(request);

            // 请求行
            log.info(">>> {} {}{} | ip={} | status={} | {}ms",
                    method, uri, (query != null ? "?" + query : ""), clientIp, status, duration);

            // 只记录 /api/** 路径的请求/响应体（静态资源容易因编码问题产生乱码，跳过）
            if (!uri.startsWith("/api/")) {
                return;
            }

            // 请求体
            byte[] reqBuf = request.getContentAsByteArray();
            if (reqBuf.length > 0) {
                String body = new String(reqBuf, 0, Math.min(reqBuf.length, MAX_BODY_LENGTH), StandardCharsets.UTF_8);
                log.info(">>> REQUEST BODY: {}", truncate(body));
            }

            // 响应体
            byte[] respBuf = response.getContentAsByteArray();
            if (respBuf.length > 0) {
                String body = new String(respBuf, 0, Math.min(respBuf.length, MAX_BODY_LENGTH), StandardCharsets.UTF_8);
                log.info("<<< RESPONSE BODY: {}", truncate(body));
            }
        } catch (Exception e) {
            log.warn("记录 HTTP 日志失败: {}", e.getMessage());
        }
    }

    private String truncate(String content) {
        if (content == null) return "";
        if (content.length() > MAX_BODY_LENGTH) {
            return content.substring(0, MAX_BODY_LENGTH) + "...(truncated)";
        }
        return content;
    }

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
