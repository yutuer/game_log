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
}
