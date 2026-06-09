package com.gamelog.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamelog.entity.GameLog;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * 数据日志写入器
 * 使用 Log4j2 的 DataLogger 将每条 GameLog 写入 JSON 文件
 * 实现数据双重保障：内存队列 + 文件备份
 */
@Slf4j
@Component
public class DataLogWriter {

    private static final Logger dataLogger = LogManager.getLogger("DataLogger");
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入 Spring 容器管理的 ObjectMapper Bean
     * 该 Bean 已由 Spring Boot 自动配置注册了 JavaTimeModule，
     * 可正确序列化 LocalDateTime 等 JSR310 时间类型
     */
    public DataLogWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将 GameLog 写入日志文件
     *
     * @return true=写入成功, false=写入失败（外层会记录 warn 日志）
     */
    public boolean logData(GameLog gameLog) {
        try {
            String json = objectMapper.writeValueAsString(gameLog);
            dataLogger.info(json);
            return true;
        } catch (Exception e) {
            log.error("数据日志写入失败: gameName={}, player={}",
                    gameLog.getGameName(), gameLog.getPlayer(), e);
            return false;
        }
    }


}