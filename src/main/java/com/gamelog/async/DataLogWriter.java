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
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将 GameLog 写入日志文件
     * 使用 Log4j2 异步写入，不阻塞主业务流程
     */
    public void logData(GameLog gameLog) {
        try {
            // 转为 JSON 字符串后写入
            String json = objectMapper.writeValueAsString(gameLog);
            dataLogger.info(json);
        } catch (Exception e) {
            // 日志写入失败不影响主流程，但需要记录
            log.error("数据日志写入失败: gameName={}, player={}",
                    gameLog.getGameName(), gameLog.getPlayer(), e);
        }
    }

    /**
     * 批量写入日志
     */
    public void logDataBatch(Iterable<GameLog> gameLogs) {
        for (GameLog gameLog : gameLogs) {
            logData(gameLog);
        }
    }
}