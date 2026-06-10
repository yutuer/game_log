package com.gamelog.service;

import com.gamelog.async.GameLogAsyncWriter;
import com.gamelog.dto.*;
import com.gamelog.entity.GameLog;
import com.gamelog.repository.GameLogDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
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

    private final GameLogDao gameLogDao;
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
        gameLog.setCreatedAt(LocalDateTime.now());
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
            gameLog.setCreatedAt(LocalDateTime.now());
            return gameLog;
        }).collect(Collectors.toList());
        return gameLogAsyncWriter.submitBatch(gameLogs);
    }

    /**
     * 分页查询日志
     */
    public PageResult<GameLog> queryGameLogs(GameLogQueryDTO queryDTO) {
        return gameLogDao.findByConditions(
                queryDTO.getGameName(),
                queryDTO.getPlayer(),
                queryDTO.getStartTime(),
                queryDTO.getEndTime(),
                queryDTO.getPage(),
                queryDTO.getSize()
        );
    }

    /**
     * 查询单条日志
     */
    public Optional<GameLog> getGameLogById(Long id) {
        return gameLogDao.findById(id);
    }

    /**
     * 删除日志
     */
    public boolean deleteGameLog(Long id) {
        if (gameLogDao.existsById(id)) {
            gameLogDao.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * 获取统计数据（聚合接口，已废弃，请使用独立接口）
     * 缓存 10 秒
     * @deprecated 使用各独立接口代替，前端可并行请求
     */
    @Deprecated
    @Cacheable(value = "stats", key = "'all'")
    public GameLogStatsDTO getStats() {
        GameLogStatsDTO stats = new GameLogStatsDTO();
        stats.setTodayCount(getTodayCount());
        stats.setTrend(getTrend());
        stats.setGameDistribution(getGameDistribution());
        stats.setRecentLogs(getRecentLogs());
        stats.setPlayerLeaderboard(getPlayerLeaderboard());
        stats.setHourlyActivity(getHourlyActivity());
        stats.setActionDistribution(getActionDistribution());
        stats.setAverageDuration(getAverageDuration());
        return stats;
    }

    // ==================== 独立统计接口 ====================

    @Cacheable(value = "stats", key = "'today-count'")
    public long getTodayCount() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        return gameLogDao.countByCreatedAtBetween(todayStart, todayEnd);
    }

    @Cacheable(value = "stats", key = "'avg-duration'")
    public double getAverageDuration() {
        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        Double avg = gameLogDao.findAverageDuration(thirtyDaysAgo, todayEnd);
        return avg != null ? avg : 0.0;
    }

    @Cacheable(value = "stats", key = "'trend'")
    public List<Map<String, Object>> getTrend() {
        LocalDateTime sevenDaysAgo = LocalDate.now().minusDays(6).atStartOfDay();
        return gameLogDao.countByDateGroup(sevenDaysAgo);
    }

    @Cacheable(value = "stats", key = "'game-dist'")
    public List<Map<String, Object>> getGameDistribution() {
        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        return gameLogDao.countByGameNameGroup(thirtyDaysAgo, todayEnd);
    }

    @Cacheable(value = "stats", key = "'recent-logs'")
    public List<GameLog> getRecentLogs() {
        return gameLogDao.findTop10ByOrderByCreatedAtDesc();
    }

    @Cacheable(value = "stats", key = "'leaderboard'")
    public List<Map<String, Object>> getPlayerLeaderboard() {
        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        List<Map<String, Object>> playerStats = gameLogDao.findPlayerStats(thirtyDaysAgo, todayEnd);
        return playerStats.stream().limit(10).collect(Collectors.toList());
    }

    @Cacheable(value = "stats", key = "'action-dist'")
    public List<Map<String, Object>> getActionDistribution() {
        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        return gameLogDao.findActionStats(thirtyDaysAgo, todayEnd);
    }

    @Cacheable(value = "stats", key = "'hourly-activity'")
    public List<Map<String, Object>> getHourlyActivity() {
        LocalDateTime sevenDaysAgo = LocalDate.now().minusDays(6).atStartOfDay();
        return gameLogDao.findDailyHourlyStats(sevenDaysAgo);
    }

    /**
     * 获取队列状态（运维监控）
     */
    public QueueStatusDTO getQueueStatus() {
        return gameLogAsyncWriter.getQueueStatus();
    }
}
