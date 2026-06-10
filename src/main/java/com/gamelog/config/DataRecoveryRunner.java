package com.gamelog.config;

import com.gamelog.entity.GameLog;
import com.gamelog.repository.GameLogDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据恢复启动器
 * 启动时检查日志文件，与数据库对比，恢复未入库的数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRecoveryRunner implements ApplicationRunner {

    private final GameLogDao gameLogDao;
    private final ObjectMapper objectMapper;

    private static final String LOG_PATH = "logs/data";
    private static final int RETENTION_DAYS = 7;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("========== 数据恢复检查启动 ==========");

        Path logDir = Paths.get(LOG_PATH);
        if (!Files.exists(logDir)) {
            log.info("日志目录不存在，跳过数据恢复: {}", logDir);
            return;
        }

        List<Path> logFiles = getRecentLogFiles(logDir, 3);
        if (logFiles.isEmpty()) {
            log.info("没有找到需要恢复的日志文件");
            return;
        }

        log.info("找到 {} 个日志文件需要检查", logFiles.size());

        int totalRecovered = 0;
        boolean allSyncSuccess = true;
        for (Path file : logFiles) {
            int count = recoverFromFile(file);
            if (count >= 0) {
                totalRecovered += count;
            } else {
                allSyncSuccess = false;
            }
        }

        cleanupOldFiles(logDir);

        if (allSyncSuccess) {
            truncateCurrentFile(Paths.get(LOG_PATH));
        } else {
            log.warn("部分文件恢复失败，不清空日志文件，下次启动将重试");
        }

        log.info("========== 数据恢复完成: 共恢复 {} 条记录 ==========", totalRecovered);
    }

    private int recoverFromFile(Path file) {
        log.info("正在恢复文件: {}", file.getFileName());

        try {
            List<String> lines = Files.readAllLines(file);
            log.info("文件共 {} 行", lines.size());

            if (lines.isEmpty()) {
                return 0;
            }

            List<GameLog> logsFromFile = new ArrayList<>();
            int parseFailCount = 0;

            for (String line : lines) {
                try {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    GameLog gameLog = objectMapper.readValue(line, GameLog.class);
                    logsFromFile.add(gameLog);
                } catch (Exception e) {
                    parseFailCount++;
                    if (parseFailCount <= 3) {
                        log.debug("解析行失败: {}", line.substring(0, Math.min(100, line.length())));
                    }
                }
            }

            if (logsFromFile.isEmpty()) {
                log.info("文件中没有有效数据");
                return 0;
            }

            log.info("解析到 {} 条有效记录", logsFromFile.size());

            Set<String> existingKeys = getExistingKeys(logsFromFile);
            List<GameLog> toRecover = logsFromFile.stream()
                    .filter(log -> !existingKeys.contains(buildKey(log)))
                    .collect(Collectors.toList());

            if (toRecover.isEmpty()) {
                log.info("所有数据已在库中，无需恢复");
                return 0;
            }

            log.info("发现 {} 条未入库记录，准备恢复", toRecover.size());

            try {
                gameLogDao.batchInsert(toRecover);
                log.info("成功恢复 {} 条记录", toRecover.size());
            } catch (Exception e) {
                log.error("批量恢复数据失败: {}, 将不清空日志文件", file.getFileName(), e);
                return -1;
            }

            return toRecover.size();

        } catch (IOException e) {
            log.error("读取日志文件失败: {}", file, e);
            return 0;
        }
    }

    private List<Path> getRecentLogFiles(Path logDir, int days) {
        List<Path> files = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 0; i < days; i++) {
            LocalDate date = LocalDate.now().minusDays(i);
            String fileName = "game-log-" + date.format(formatter) + ".jsonl";
            Path file = logDir.resolve(fileName);
            if (Files.exists(file)) {
                files.add(file);
            }
        }

        Path currentFile = logDir.resolve("game-log.jsonl");
        if (Files.exists(currentFile)) {
            files.add(currentFile);
        }

        return files;
    }

    private Set<String> getExistingKeys(List<GameLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return Collections.emptySet();
        }

        LocalDateTime minTime = logs.stream()
                .map(GameLog::getPlayTime)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        LocalDateTime maxTime = logs.stream()
                .map(GameLog::getPlayTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        if (minTime == null || maxTime == null) {
            log.debug("日志记录中 playTime 为空，无法查询数据库进行对比");
            return Collections.emptySet();
        }

        List<GameLog> dbLogs = gameLogDao.findByPlayTimeBetween(minTime, maxTime);
        Set<String> existingKeys = dbLogs.stream()
                .map(this::buildKey)
                .collect(Collectors.toSet());

        log.debug("数据库查询到 {} 条已有记录 (时间范围: {} ~ {})", existingKeys.size(), minTime, maxTime);

        return existingKeys;
    }

    private String buildKey(GameLog log) {
        return String.format("%s|%s|%s|%s",
                log.getGameName(),
                log.getPlayer(),
                log.getAction(),
                log.getPlayTime());
    }

    private void cleanupOldFiles(Path logDir) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate cutoffDate = LocalDate.now().minusDays(RETENTION_DAYS);

            Files.list(logDir)
                    .filter(path -> path.toString().endsWith(".jsonl"))
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        if (fileName.contains("-")) {
                            String dateStr = fileName.replace("game-log-", "").replace(".jsonl", "");
                            try {
                                LocalDate fileDate = LocalDate.parse(dateStr, formatter);
                                return fileDate.isBefore(cutoffDate);
                            } catch (Exception e) {
                                return false;
                            }
                        }
                        return false;
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            log.info("删除过期日志文件: {}", path.getFileName());
                        } catch (IOException e) {
                            log.warn("删除文件失败: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("清理过期文件失败", e);
        }
    }

    private void truncateCurrentFile(Path logDir) {
        Path currentFile = logDir.resolve("game-log.jsonl");
        if (!Files.exists(currentFile)) {
            return;
        }
        try {
            long size = Files.size(currentFile);
            Files.write(currentFile, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
            log.info("数据同步完成，已清空当前日志文件 (原大小 {} 字节)", size);
        } catch (IOException e) {
            log.warn("清空日志文件失败，不影响已有数据，下次启动将重试", e);
        }
    }
}
