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
     * 获取统计数据（聚合接口，已废弃，请使用独立接口）
     * 缓存 10 秒
     * @deprecated 使用各独立接口代替，前端可并行请求
     */
    @Deprecated
    @Cacheable(value = "stats", key = "'all'")
    public GameLogStatsDTO getStats() {
        // 调用各独立方法组装数据
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

    // ==================== 独立统计接口（前端渐进式加载） ====================
    // 排序原则：按页面展示顺序，耗时少的在前

    /**
     * 1. 今日日志总数（页面顶部，一眼可见）
     * 缓存 10 秒
     */
    @Cacheable(value = "stats", key = "'today-count'")
    public long getTodayCount() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        return gameLogRepository.countByCreatedAtBetween(todayStart, todayEnd);
    }

    /**
     * 2. 平均游戏时长（页面顶部，一眼可见）
     * 缓存 10 秒
     */
    @Cacheable(value = "stats", key = "'avg-duration'")
    public double getAverageDuration() {
        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        Double avg = gameLogRepository.findAverageDuration(thirtyDaysAgo, todayEnd);
        return avg != null ? avg : 0.0;
    }

    /**
     * 3. 近7天趋势（页面第4块）
     * 缓存 10 秒
     */
    @Cacheable(value = "stats", key = "'trend'")
    public List<Map<String, Object>> getTrend() {
        LocalDateTime sevenDaysAgo = LocalDate.now().minusDays(6).atStartOfDay();
        List<Object[]> dateCounts = gameLogRepository.countByDateGroup(sevenDaysAgo);
        return dateCounts.stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("date", row[0].toString());
            item.put("count", row[1]);
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 4. 游戏分布占比（页面第5块）
     * 缓存 10 秒
     */
    @Cacheable(value = "stats", key = "'game-dist'")
    public List<Map<String, Object>> getGameDistribution() {
        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        List<Object[]> gameCounts = gameLogRepository.countByGameNameGroup(thirtyDaysAgo, todayEnd);
        return gameCounts.stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("gameName", row[0].toString());
            item.put("count", row[1]);
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 5. 最近日志（页面第6块，TOP 10）
     * 缓存 10 秒
     */
    @Cacheable(value = "stats", key = "'recent-logs'")
    public List<GameLog> getRecentLogs() {
        return gameLogRepository.findTop10ByOrderByCreatedAtDesc();
    }

    /**
     * 6. 玩家排行榜 Top 10（页面第7块）
     * 缓存 10 秒
     */
    @Cacheable(value = "stats", key = "'leaderboard'")
    public List<Map<String, Object>> getPlayerLeaderboard() {
        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        List<Object[]> playerStats = gameLogRepository.findPlayerStats(thirtyDaysAgo, todayEnd);
        return playerStats.stream()
                .limit(10)
                .map(row -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("player", row[0].toString());
                    item.put("count", row[1]);
                    return item;
                }).collect(Collectors.toList());
    }

    /**
     * 7. 操作类型分布（页面第9块）
     * 缓存 10 秒
     */
    @Cacheable(value = "stats", key = "'action-dist'")
    public List<Map<String, Object>> getActionDistribution() {
        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(29).atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);
        List<Object[]> actionStats = gameLogRepository.findActionStats(thirtyDaysAgo, todayEnd);
        return actionStats.stream().map(row -> {
            Map<String, Object> item = new HashMap<>();
            item.put("action", row[0].toString());
            item.put("count", row[1]);
            return item;
        }).collect(Collectors.toList());
    }

    /**
     * 8. 24小时活跃热力图（页面第8块，查询最重，放最后）
     * 缓存 10 秒
     */
    @Cacheable(value = "stats", key = "'hourly-activity'")
    public List<Map<String, Object>> getHourlyActivity() {
        LocalDateTime sevenDaysAgo = LocalDate.now().minusDays(6).atStartOfDay();
        List<Object[]> dailyHourlyStats = gameLogRepository.findDailyHourlyStats(sevenDaysAgo);
        List<Map<String, Object>> hourlyActivity = new ArrayList<>();
        for (Object[] row : dailyHourlyStats) {
            Map<String, Object> item = new HashMap<>();
            // DATE 返回的是 java.sql.Date，转为字符串
            item.put("date", row[0].toString());
            item.put("hour", row[1]);
            item.put("count", row[2]);
            hourlyActivity.add(item);
        }
        return hourlyActivity;
    }

    /**
     * 获取队列状态（运维监控）
     */
    public QueueStatusDTO getQueueStatus() {
        return gameLogAsyncWriter.getQueueStatus();
    }
}
