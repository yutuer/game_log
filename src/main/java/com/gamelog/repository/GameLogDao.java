package com.gamelog.repository;

import com.gamelog.dto.PageResult;
import com.gamelog.entity.GameLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 游戏日志 DAO（纯 JDBC 实现，替代 Hibernate/JPA）
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class GameLogDao {

    private final JdbcTemplate jdbcTemplate;

    private static final String ALL_COLUMNS = "id, game_name, player, action, detail, play_time, duration, created_at";

    private final RowMapper<GameLog> rowMapper = (rs, rowNum) -> {
        GameLog g = new GameLog();
        g.setId(rs.getLong("id"));
        g.setGameName(rs.getString("game_name"));
        g.setPlayer(rs.getString("player"));
        g.setAction(rs.getString("action"));
        g.setDetail(rs.getString("detail"));
        Timestamp pt = rs.getTimestamp("play_time");
        if (pt != null) g.setPlayTime(pt.toLocalDateTime());
        g.setDuration(rs.getObject("duration", Integer.class));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) g.setCreatedAt(ca.toLocalDateTime());
        return g;
    };

    // ==================== 基础 CRUD ====================

    public Optional<GameLog> findById(Long id) {
        List<GameLog> list = jdbcTemplate.query(
                "SELECT " + ALL_COLUMNS + " FROM game_log WHERE id = ?",
                rowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM game_log WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM game_log WHERE id = ?", id);
    }

    /**
     * 批量插入（用于数据恢复）
     */
    public void batchInsert(List<GameLog> logs) {
        String sql = "INSERT INTO game_log (game_name, player, action, detail, play_time, duration, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        List<Object[]> batchArgs = new ArrayList<>(logs.size());
        for (GameLog g : logs) {
            batchArgs.add(new Object[]{
                g.getGameName(),
                g.getPlayer(),
                g.getAction(),
                g.getDetail(),
                g.getPlayTime() != null ? Timestamp.valueOf(g.getPlayTime()) : null,
                g.getDuration(),
                g.getCreatedAt() != null ? Timestamp.valueOf(g.getCreatedAt()) : Timestamp.valueOf(LocalDateTime.now())
            });
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    // ==================== 查询接口 ====================

    /**
     * 动态条件分页查询
     */
    public PageResult<GameLog> findByConditions(String gameName, String player,
                                                 LocalDateTime startTime, LocalDateTime endTime,
                                                 int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (gameName != null && !gameName.isEmpty()) {
            where.append(" AND game_name = ?");
            params.add(gameName);
        }
        if (player != null && !player.isEmpty()) {
            where.append(" AND player = ?");
            params.add(player);
        }
        if (startTime != null) {
            where.append(" AND play_time >= ?");
            params.add(Timestamp.valueOf(startTime));
        }
        if (endTime != null) {
            where.append(" AND play_time <= ?");
            params.add(Timestamp.valueOf(endTime));
        }

        // 总数
        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM game_log" + where,
                Long.class, params.toArray());

        // 分页数据
        int offset = page * size;
        String sql = "SELECT " + ALL_COLUMNS + " FROM game_log" + where +
                     " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        params.add(size);
        params.add(offset);

        List<GameLog> content = jdbcTemplate.query(sql, rowMapper, params.toArray());
        return new PageResult<>(content, page, size, total);
    }

    /**
     * 按时间范围查询（用于数据恢复去重）
     */
    public List<GameLog> findByPlayTimeBetween(LocalDateTime minTime, LocalDateTime maxTime) {
        return jdbcTemplate.query(
                "SELECT " + ALL_COLUMNS + " FROM game_log WHERE play_time BETWEEN ? AND ?",
                rowMapper, Timestamp.valueOf(minTime), Timestamp.valueOf(maxTime));
    }

    // ==================== 统计接口 ====================

    public long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM game_log WHERE created_at BETWEEN ? AND ?",
                Long.class, Timestamp.valueOf(start), Timestamp.valueOf(end));
        return count != null ? count : 0L;
    }

    public long countTotal() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM game_log", Long.class);
        return count != null ? count : 0L;
    }

    public List<GameLog> findTop10ByOrderByCreatedAtDesc() {
        return jdbcTemplate.query(
                "SELECT " + ALL_COLUMNS + " FROM game_log ORDER BY created_at DESC LIMIT 10",
                rowMapper);
    }

    // ==================== 聚合统计（返回 Map 列表） ====================

    /**
     * 按游戏名称分组统计
     */
    public List<Map<String, Object>> countByGameNameGroup(LocalDateTime start, LocalDateTime end) {
        return jdbcTemplate.query(
                "SELECT game_name, COUNT(*) AS cnt FROM game_log " +
                "WHERE created_at BETWEEN ? AND ? GROUP BY game_name ORDER BY cnt DESC LIMIT 50",
                (rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("gameName", rs.getString("game_name"));
                    row.put("count", rs.getLong("cnt"));
                    return row;
                },
                Timestamp.valueOf(start), Timestamp.valueOf(end));
    }

    /**
     * 按日期分组统计
     */
    public List<Map<String, Object>> countByDateGroup(LocalDateTime start) {
        return jdbcTemplate.query(
                "SELECT DATE(created_at) AS day, COUNT(*) AS cnt FROM game_log " +
                "WHERE created_at >= ? GROUP BY DATE(created_at) ORDER BY day",
                (rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("date", rs.getDate("day").toString());
                    row.put("count", rs.getLong("cnt"));
                    return row;
                },
                Timestamp.valueOf(start));
    }

    /**
     * 玩家排行榜
     */
    public List<Map<String, Object>> findPlayerStats(LocalDateTime start, LocalDateTime end) {
        return jdbcTemplate.query(
                "SELECT player, COUNT(*) AS cnt FROM game_log " +
                "WHERE created_at BETWEEN ? AND ? GROUP BY player ORDER BY cnt DESC LIMIT 100",
                (rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("player", rs.getString("player"));
                    row.put("count", rs.getLong("cnt"));
                    return row;
                },
                Timestamp.valueOf(start), Timestamp.valueOf(end));
    }

    /**
     * 按天和小时分组统计
     */
    public List<Map<String, Object>> findDailyHourlyStats(LocalDateTime start) {
        return jdbcTemplate.query(
                "SELECT DATE(created_at) AS day, HOUR(created_at) AS hour, COUNT(*) AS cnt " +
                "FROM game_log WHERE created_at >= ? GROUP BY day, hour ORDER BY day, hour",
                (rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("date", rs.getDate("day").toString());
                    row.put("hour", rs.getInt("hour"));
                    row.put("count", rs.getLong("cnt"));
                    return row;
                },
                Timestamp.valueOf(start));
    }

    /**
     * 操作类型分布
     */
    public List<Map<String, Object>> findActionStats(LocalDateTime start, LocalDateTime end) {
        return jdbcTemplate.query(
                "SELECT action, COUNT(*) AS cnt FROM game_log " +
                "WHERE created_at BETWEEN ? AND ? GROUP BY action ORDER BY cnt DESC LIMIT 20",
                (rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("action", rs.getString("action"));
                    row.put("count", rs.getLong("cnt"));
                    return row;
                },
                Timestamp.valueOf(start), Timestamp.valueOf(end));
    }

    /**
     * 平均游戏时长
     */
    public Double findAverageDuration(LocalDateTime start, LocalDateTime end) {
        return jdbcTemplate.queryForObject(
                "SELECT AVG(duration) FROM game_log WHERE duration IS NOT NULL AND created_at BETWEEN ? AND ?",
                Double.class, Timestamp.valueOf(start), Timestamp.valueOf(end));
    }
}
