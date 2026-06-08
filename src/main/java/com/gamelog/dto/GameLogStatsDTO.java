package com.gamelog.dto;

import com.gamelog.entity.GameLog;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class GameLogStatsDTO {
    private long todayCount;
    private List<Map<String, Object>> trend;      // [{date: "2026-06-01", count: 100}, ...]
    private List<Map<String, Object>> gameDistribution; // [{gameName: "xxx", count: 50}, ...]
    private List<GameLog> recentLogs;

    // 扩展字段
    private List<Map<String, Object>> playerLeaderboard;      // 玩家排行榜
    private List<Map<String, Object>> hourlyActivity;         // 24小时活跃时段
    private List<Map<String, Object>> actionDistribution;    // 操作类型分布
    private Double averageDuration;                           // 平均游戏时长
}
