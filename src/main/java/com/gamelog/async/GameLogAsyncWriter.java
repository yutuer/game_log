package com.gamelog.async;

import com.gamelog.config.AsyncConfig;
import com.gamelog.entity.GameLog;
import com.gamelog.repository.GameLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Component
public class GameLogAsyncWriter {

    private final GameLogRepository gameLogRepository;
    private final AsyncConfig asyncConfig;
    private final BlockingQueue<GameLog> queue;

    public GameLogAsyncWriter(GameLogRepository gameLogRepository, AsyncConfig asyncConfig) {
        this.gameLogRepository = gameLogRepository;
        this.asyncConfig = asyncConfig;
        this.queue = new LinkedBlockingQueue<>(asyncConfig.getQueueCapacity());
        startFlushThread();
    }

    /**
     * 提交日志到异步队列，队列满时降级为同步写入
     */
    public boolean submit(GameLog gameLog) {
        int currentSize = queue.size();
        int capacity = queue.remainingCapacity();

        // 压力告警日志
        if (currentSize > 0 && currentSize % 500 == 0) {
            log.warn("[PRESSURE] Queue backlog high: size={}, capacity={}, gameName={}",
                    currentSize, capacity, gameLog.getGameName());
        }

        boolean offered = queue.offer(gameLog);
        if (!offered) {
            log.error("[PRESSURE] Queue FULL! Falling back to sync write: gameName={}", gameLog.getGameName());
            gameLogRepository.save(gameLog);
            return false;
        }
        return true;
    }

    /**
     * 批量提交日志到异步队列
     */
    public int submitBatch(List<GameLog> gameLogs) {
        int asyncCount = 0;
        for (GameLog gameLog : gameLogs) {
            if (submit(gameLog)) {
                asyncCount++;
            }
        }
        return asyncCount;
    }

    /**
     * 获取当前队列积压数量
     */
    public int getQueueSize() {
        return queue.size();
    }

    private void startFlushThread() {
        Thread flushThread = new Thread(() -> {
            List<GameLog> batch = new ArrayList<>(asyncConfig.getBatchSize());
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 等待第一条日志，最多等 flushIntervalMs
                    GameLog first = queue.poll();
                    if (first != null) {
                        batch.add(first);
                    }

                    // 积攒批量
                    queue.drainTo(batch, asyncConfig.getBatchSize() - batch.size());

                    if (!batch.isEmpty()) {
                        flushBatch(batch);
                        batch.clear();
                    } else {
                        // 没有数据，等待一小段时间
                        Thread.sleep(asyncConfig.getFlushIntervalMs());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // 关闭前刷新剩余数据
                    queue.drainTo(batch);
                    if (!batch.isEmpty()) {
                        flushBatch(batch);
                    }
                    break;
                } catch (Exception e) {
                    log.error("异步写入异常", e);
                }
            }
        }, "gamelog-flush");
        flushThread.setDaemon(true);
        flushThread.start();
        log.info("Async flush thread started: batchSize={}, flushInterval={}ms, queueCapacity={}",
                asyncConfig.getBatchSize(), asyncConfig.getFlushIntervalMs(), asyncConfig.getQueueCapacity());
    }

    private void flushBatch(List<GameLog> batch) {
        long start = System.currentTimeMillis();
        try {
            gameLogRepository.saveAll(batch);
            long cost = System.currentTimeMillis() - start;
            log.info("[ASYNC-WRITE] Batch write: size={}, cost={}ms, queue_remaining={}",
                    batch.size(), cost, queue.size());
        } catch (Exception e) {
            log.error("[ASYNC-WRITE] Batch write failed, trying one by one: size={}", batch.size(), e);
            for (GameLog gameLog : batch) {
                try {
                    gameLogRepository.save(gameLog);
                } catch (Exception ex) {
                    log.error("单条写入失败: gameName={}", gameLog.getGameName(), ex);
                }
            }
        }
    }
}