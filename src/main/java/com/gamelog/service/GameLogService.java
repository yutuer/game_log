package com.gamelog.service;

import com.gamelog.async.GameLogAsyncWriter;
import com.gamelog.dto.*;
import com.gamelog.entity.GameLog;
import com.gamelog.repository.GameLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameLogService {

    private final GameLogRepository gameLogRepository;
    private final GameLogAsyncWriter gameLogAsyncWriter;

    /**
     * 异步新增日志
     */
    public boolean createGameLog(GameLogCreateDTO dto) {
        GameLog gameLog = new GameLog();
        gameLog.setGameName(dto.getGameName());
        gameLog.setPlayer(dto.getPlayer());
        gameLog.setAction(dto.getAction());
        gameLog.setDetail(dto.getDetail());
        gameLog.setPlayTime(dto.getPlayTime());
        gameLog.setDuration(dto.getDuration());
        return gameLogAsyncWriter.submit(gameLog);
    }

    /**
     * 批量异步新增日志
     */
    public int createGameLogBatch(List<GameLogCreateDTO> logs) {
        List<GameLog> gameLogs = logs.stream().map(item -> {
            GameLog gameLog = new GameLog();
            gameLog.setGameName(item.getGameName());
            gameLog.setPlayer(item.getPlayer());
            gameLog.setAction(item.getAction());
            gameLog.setDetail(item.getDetail());
            gameLog.setPlayTime(item.getPlayTime());
            gameLog.setDuration(item.getDuration());
            return gameLog;
        }).collect(Collectors.toList());
        return gameLogAsyncWriter.submitBatch(gameLogs);
    }

    /**
     * 分页查询日志
     */
    public Page<GameLog> queryGameLogs(GameLogQueryDTO queryDTO) {
        PageRequest pageRequest = PageRequest.of(
                queryDTO.getPage(),
                queryDTO.getSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return gameLogRepository.findByConditions(
                queryDTO.getGameName(),
                queryDTO.getPlayer(),
                queryDTO.getStartTime(),
                queryDTO.getEndTime(),
                pageRequest
        );
    }

    /**
     * 查询单条日志
     */
    public Optional<GameLog> getGameLogById(Long id) {
        return gameLogRepository.findById(id);
    }

    /**
     * 删除日志
     */
    public boolean deleteGameLog(Long id) {
        if (gameLogRepository.existsById(id)) {
            gameLogRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * 获取统计数据
     * 缓存 30 秒，避免频繁查询数据库
     */
    @Cacheable(value = "gameLogStats", key = "'all'")
    public GameLogStatsDTO getStats() {
        GameLogStatsDTO stats = new GameLogStatsDTO();

        // 今日总数
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        stats.setTodayCount(gameLogRepository.countByCreatedAtBetween(todayStart, todayEnd));

        // 近7天趋势
        LocalDateTime sevenDaysAgo = LocalDate.now().minusDays(6).atStartOfDay();
        List<Object[]> dateCounts = gameLogRepository.countByDateGroup(sevenDaysAgo);
        List<Map<String, Object>> trend = dateCounts.stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("date", row[0].toString());
            item.put("count", row[1]);
            return item;
        }).collect(Collectors.toList());
        stats.setTrend(trend);

        // 游戏占比（近30天）
        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(29).atStartOfDay();
        List<Object[]> gameCounts = gameLogRepository.countByGameNameGroup(thirtyDaysAgo, todayEnd);
        List<Map<String, Object>> gameDistribution = gameCounts.stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("gameName", row[0].toString());
            item.put("count", row[1]);
            return item;
        }).collect(Collectors.toList());
        stats.setGameDistribution(gameDistribution);

        // 最近日志
        stats.setRecentLogs(gameLogRepository.findTop10ByOrderByCreatedAtDesc());

        // 玩家排行榜 Top 10
        List<Object[]> playerStats = gameLogRepository.findPlayerStats(thirtyDaysAgo, todayEnd);
        List<Map<String, Object>> playerLeaderboard = playerStats.stream()
                .limit(10)
                .map(row -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("player", row[0].toString());
                    item.put("count", row[1]);
                    return item;
                }).collect(Collectors.toList());
        stats.setPlayerLeaderboard(playerLeaderboard);

        // 24小时活跃时段分布（过去7天，按天×小时分组）
        LocalDateTime sevenDaysAgoForHeatmap = LocalDate.now().minusDays(6).atStartOfDay();
        List<Object[]> dailyHourlyStats = gameLogRepository.findDailyHourlyStats(sevenDaysAgoForHeatmap);
        List<Map<String, Object>> hourlyActivity = new ArrayList<>();
        for (Object[] row : dailyHourlyStats) {
            Map<String, Object> item = new HashMap<>();
            // DATE 返回的是 java.sql.Date，转为字符串
            item.put("date", row[0].toString());
            item.put("hour", row[1]);
            item.put("count", row[2]);
            hourlyActivity.add(item);
        }
        stats.setHourlyActivity(hourlyActivity);

        // 操作类型分布
        List<Object[]> actionStats = gameLogRepository.findActionStats(thirtyDaysAgo, todayEnd);
        List<Map<String, Object>> actionDistribution = actionStats.stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("action", row[0].toString());
            item.put("count", row[1]);
            return item;
        }).collect(Collectors.toList());
        stats.setActionDistribution(actionDistribution);

        // 平均游戏时长
        Double averageDuration = gameLogRepository.findAverageDuration(thirtyDaysAgo, todayEnd);
        stats.setAverageDuration(averageDuration != null ? averageDuration : 0.0);

        return stats;
    }

    /**
     * 获取队列状态（运维监控）
     */
    public QueueStatusDTO getQueueStatus() {
        return gameLogAsyncWriter.getQueueStatus();
    }
}
