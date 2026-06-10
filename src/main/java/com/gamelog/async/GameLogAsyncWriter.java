package com.gamelog.async;

import com.gamelog.config.AsyncConfig;
import com.gamelog.dto.QueueStatusDTO;
import com.gamelog.entity.GameLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class GameLogAsyncWriter {

    private final DataSource dataSource;
    private final AsyncConfig asyncConfig;
    private final DataLogWriter dataLogWriter;
    private final BlockingQueue<GameLog> queue;

    // 监控计数器
    private final AtomicLong batchWriteCount = new AtomicLong(0);
    private final AtomicLong totalWriteCount = new AtomicLong(0);
    private volatile long lastFlushTime = 0L;
    private volatile long backlogWarnTime = 0L;
    private static final long BACKLOG_WARN_COOLDOWN_MS = 30_000L;

    // flush 线程引用，用于关闭时协调
    private volatile Thread flushThread;

    private static final String INSERT_SQL = "INSERT INTO game_log (game_name, player, action, detail, play_time, duration, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

    public GameLogAsyncWriter(DataSource dataSource,
                              AsyncConfig asyncConfig,
                              DataLogWriter dataLogWriter) {
        this.dataSource = dataSource;
        this.asyncConfig = asyncConfig;
        this.dataLogWriter = dataLogWriter;
        this.queue = new LinkedBlockingQueue<>(asyncConfig.getQueueCapacity());
        startFlushThread();
        registerShutdownHook();
    }

    /**
     * 注册关闭钩子，确保服务关闭时将队列中剩余数据写入数据库
     *
     * 策略：
     * 1. 先中断 flush 线程，触发其 drain+flush 当前批次
     * 2. 等待 flush 线程结束（最多 5 秒），避免重复 drain
     * 3. 若队列还有剩余，再 flush 一次
     * 这样既不会重复入库，也不会丢数据
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("[SHUTDOWN] 服务关闭中，等待 flush 线程完成当前批次...");

            // 1. 中断 flush 线程（会触发 InterruptedException 分支中的 drain+flush）
            if (flushThread != null) {
                flushThread.interrupt();
            }
            if (flushThread != null) {
                try {
                    flushThread.join(5000); // 最多等 5 秒
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // 2. 如果队列还有剩余（flush 线程没来得及处理的），再 flush 一次
            if (queue.isEmpty()) {
                log.info("[SHUTDOWN] 队列已空，无需处理");
            } else {
                List<GameLog> remaining = new ArrayList<>();
                queue.drainTo(remaining);
                if (!remaining.isEmpty()) {
                    log.info("[SHUTDOWN] 队列中还有 {} 条数据需要入库", remaining.size());
                    jdbcBatchInsert(remaining);
                }
            }

            log.info("[SHUTDOWN] 关闭钩子执行完成");
        }, "gamelog-shutdown-hook"));
    }

    /**
     * 提交日志到异步队列，队列满时直接丢弃（数据已写入日志文件备份）
     */
    public boolean submit(GameLog gameLog) {
        // 先写入日志文件（数据备份）
        boolean fileWritten = dataLogWriter.logData(gameLog);
        if (!fileWritten) {
            log.warn("[FILE] 日志文件写入失败，数据仅靠内存队列保障: gameName={}, player={}",
                    gameLog.getGameName(), gameLog.getPlayer());
        }

        int backlog = queue.size();
        int remainingCapacity = queue.remainingCapacity();

        // 压力告警：超过阈值（15000）时告警，同一波高压每隔 30 秒重复告警一次
        if (backlog >= 15000) {
            long now = System.currentTimeMillis();
            if (backlogWarnTime == 0 || now - backlogWarnTime > BACKLOG_WARN_COOLDOWN_MS) {
                backlogWarnTime = now;
                log.warn("[PRESSURE] Queue backlog high: backlog={}, remainingCapacity={}, totalCapacity={}, gameName={}",
                        backlog, remainingCapacity, asyncConfig.getQueueCapacity(), gameLog.getGameName());
            }
        } else {
            backlogWarnTime = 0; // 降到阈值以下，复位
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
        Thread t = new Thread(() -> {
            List<GameLog> batch = new ArrayList<>(asyncConfig.getBatchSize());
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    GameLog first = queue.poll();
                    if (first != null) {
                        batch.add(first);
                    }

                    queue.drainTo(batch, asyncConfig.getBatchSize() - batch.size());

                    if (!batch.isEmpty()) {
                        flushBatch(batch);
                        batch.clear();
                    } else {
                        Thread.sleep(asyncConfig.getFlushIntervalMs());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
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
        t.setDaemon(true);
        t.start();
        flushThread = t;
        log.info("Async flush thread started: batchSize={}, flushInterval={}ms, queueCapacity={}",
                asyncConfig.getBatchSize(), asyncConfig.getFlushIntervalMs(), asyncConfig.getQueueCapacity());
    }

    /**
     * JDBC batch insert：绕过 Hibernate，直接使用 PreparedStatement + executeBatch
     */
    private void flushBatch(List<GameLog> batch) {
        long start = System.currentTimeMillis();
        totalWriteCount.addAndGet(batch.size());
        jdbcBatchInsert(batch);
        batchWriteCount.incrementAndGet();
        lastFlushTime = System.currentTimeMillis();
        long cost = System.currentTimeMillis() - start;
        log.info("[ASYNC-WRITE] Batch write: size={}, cost={}ms, queue_remaining={}",
                batch.size(), cost, queue.size());
    }

    /**
     * 执行 JDBC batch INSERT
     * 主键使用 MySQL AUTO_INCREMENT，省掉 id_sequence 管理和 FOR UPDATE 锁开销
     */
    private void jdbcBatchInsert(List<GameLog> batch) {
        if (batch.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        for (GameLog g : batch) {
            if (g.getCreatedAt() == null) {
                g.setCreatedAt(now);
            }
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            conn.setAutoCommit(false);

            for (GameLog g : batch) {
                ps.setString(1, g.getGameName());
                ps.setString(2, g.getPlayer());
                ps.setString(3, g.getAction());
                ps.setString(4, g.getDetail());
                ps.setTimestamp(5, Timestamp.valueOf(g.getPlayTime()));
                if (g.getDuration() != null) {
                    ps.setInt(6, g.getDuration());
                } else {
                    ps.setNull(6, Types.INTEGER);
                }
                ps.setTimestamp(7, Timestamp.valueOf(g.getCreatedAt()));
                ps.addBatch();
            }

            ps.executeBatch();
            conn.commit();
        } catch (Exception e) {
            log.error("[JDBC] Batch INSERT 失败，数据已写入日志文件，重启后可恢复: size={}", batch.size(), e);
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
