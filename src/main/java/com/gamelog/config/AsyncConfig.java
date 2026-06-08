package com.gamelog.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Data
@Configuration
@EnableAsync
@ConfigurationProperties(prefix = "async.writer")
public class AsyncConfig {

    private int corePoolSize = 2;
    private int maxPoolSize = 4;
    private int queueCapacity = 10000;
    private int batchSize = 100;
    private long flushIntervalMs = 1000;

    @Bean(name = "gameLogAsyncExecutor")
    public Executor gameLogAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("gamelog-async-");
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("异步写入队列已满，降级为同步写入");
            r.run();
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("异步写入线程池初始化完成: core={}, max={}, queue={}", corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }
}
