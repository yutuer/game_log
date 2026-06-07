package com.gamelog.async;

import com.gamelog.config.AsyncConfig;
import com.gamelog.entity.GameLog;
import com.gamelog.repository.GameLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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
        boolean offered = queue.offer(gameLog);
        if (!offered) {
            log.warn("异步队列已满，降级为同步写入: gameName={}", gameLog.getGameName());
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
        log.info("异步写入刷新线程已启动: batchSize={}, flushIntervalMs={}",
                asyncConfig.getBatchSize(), asyncConfig.getFlushIntervalMs());
    }

    private void flushBatch(List<GameLog> batch) {
        try {
            gameLogRepository.saveAll(batch);
            log.debug("批量写入日志: {}条", batch.size());
        } catch (Exception e) {
            log.error("批量写入日志失败，尝试逐条写入: {}条", batch.size(), e);
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
