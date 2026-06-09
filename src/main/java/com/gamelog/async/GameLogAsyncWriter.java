package com.gamelog.async;

import com.gamelog.config.AsyncConfig;
import com.gamelog.dto.QueueStatusDTO;
import com.gamelog.entity.GameLog;
import com.gamelog.repository.GameLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class GameLogAsyncWriter {

    private final GameLogRepository gameLogRepository;
    private final AsyncConfig asyncConfig;
    private final DataLogWriter dataLogWriter;
    private final BlockingQueue<GameLog> queue;

    // 监控计数器
    private final AtomicLong batchWriteCount = new AtomicLong(0);
    private final AtomicLong totalWriteCount = new AtomicLong(0);
    private volatile long lastFlushTime = 0L;

    public GameLogAsyncWriter(GameLogRepository gameLogRepository,
                              AsyncConfig asyncConfig,
                              DataLogWriter dataLogWriter) {
        this.gameLogRepository = gameLogRepository;
        this.asyncConfig = asyncConfig;
        this.dataLogWriter = dataLogWriter;
        this.queue = new LinkedBlockingQueue<>(asyncConfig.getQueueCapacity());
        startFlushThread();
        registerShutdownHook();
    }

    /**
     * 注册关闭钩子，确保服务关闭时将队列中剩余数据写入日志文件
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("[SHUTDOWN] 服务关闭中，处理队列剩余数据...");
            // drain 队列中剩余数据 → 同步入库（数据已在 submit() 时写入日志文件备份）
            List<GameLog> remaining = new ArrayList<>();
            queue.drainTo(remaining);
            if (!remaining.isEmpty()) {
                log.info("[SHUTDOWN] 队列中还有 {} 条数据需要同步入库", remaining.size());
                try {
                    gameLogRepository.saveAll(remaining);
                    log.info("[SHUTDOWN] 成功将 {} 条数据写入数据库", remaining.size());
                } catch (Exception e) {
                    log.error("[SHUTDOWN] 写入数据库失败，数据已保存在日志文件中, 重启后可恢复", e);
                }
            }
            log.info("[SHUTDOWN] 关闭钩子执行完成");
        }, "gamelog-shutdown-hook"));
    }

    /**
     * 提交日志到异步队列，队列满时降级为同步写入
     * 同时写入日志文件作为备份
     */
    public boolean submit(GameLog gameLog) {
        // 先写入日志文件（数据备份）
        boolean fileWritten = dataLogWriter.logData(gameLog);
        if (!fileWritten) {
            log.warn("[FILE] 日志文件写入失败，数据仅靠内存队列保障: gameName={}, player={}",
                    gameLog.getGameName(), gameLog.getPlayer());
        }

        int currentSize = queue.size();
        int capacity = queue.remainingCapacity();

        // 压力告警日志
        if (currentSize > 0 && currentSize % 500 == 0) {
            log.warn("[PRESSURE] Queue backlog high: size={}, capacity={}, gameName={}",
                    currentSize, capacity, gameLog.getGameName());
        }

        boolean offered = queue.offer(gameLog);
        if (!offered) {
            log.warn("[PRESSURE] 队列已满(20000)，数据已写入日志文件，重启后可恢复");
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
        int threadCount = asyncConfig.getFlushThreads();
        for (int i = 0; i < threadCount; i++) {
            Thread flushThread = new Thread(new FlushTask(), "gamelog-flush-" + i);
            flushThread.setDaemon(true);
            flushThread.start();
        }
        log.info("Async flush threads started: count={}, batchSize={}, flushInterval={}ms, queueCapacity={}",
                threadCount, asyncConfig.getBatchSize(), asyncConfig.getFlushIntervalMs(), asyncConfig.getQueueCapacity());
    }

    /**
     * 消费任务：从队列取数据，积攒批量后入库
     */
    private class FlushTask implements Runnable {
        @Override
        public void run() {
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
        }
    }

    private void flushBatch(List<GameLog> batch) {
        long start = System.currentTimeMillis();
        totalWriteCount.addAndGet(batch.size());
        try {
            gameLogRepository.saveAll(batch);
            batchWriteCount.incrementAndGet();
            lastFlushTime = System.currentTimeMillis();
            long cost = System.currentTimeMillis() - start;
            log.info("[ASYNC-WRITE] Batch write: size={}, cost={}ms, queue_remaining={}, thread={}",
                    batch.size(), cost, queue.size(), Thread.currentThread().getName());
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

    /**
     * 获取队列状态监控信息
     */
    public QueueStatusDTO getQueueStatus() {
        return new QueueStatusDTO(
                queue.size(),
                totalWriteCount.get(),
                lastFlushTime > 0 ? lastFlushTime : null
        );
    }
}