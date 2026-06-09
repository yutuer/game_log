package com.gamelog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 异步写入配置
 * 由 GameLogAsyncWriter 读取，控制内存队列容量、批大小和刷写间隔
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "async.writer")
public class AsyncConfig {

    /** 内存队列最大容量 */
    private int queueCapacity = 20000;

    /** 批量入库大小 */
    private int batchSize = 200;

    /** 队列空时睡眠时间（ms） */
    private long flushIntervalMs = 500;
}