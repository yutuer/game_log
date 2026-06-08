package com.gamelog.repository;

import com.gamelog.entity.GameLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface GameLogRepository extends JpaRepository<GameLog, Long> {

    Page<GameLog> findByGameName(String gameName, Pageable pageable);

    Page<GameLog> findByPlayer(String player, Pageable pageable);

    Page<GameLog> findByPlayTimeBetween(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    @Query("SELECT g FROM GameLog g WHERE " +
           "(:gameName IS NULL OR g.gameName = :gameName) AND " +
           "(:player IS NULL OR g.player = :player) AND " +
           "(:startTime IS NULL OR g.playTime >= :startTime) AND " +
           "(:endTime IS NULL OR g.playTime <= :endTime)")
    Page<GameLog> findByConditions(
            @Param("gameName") String gameName,
            @Param("player") String player,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    // 统计接口：今日总数
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 统计接口：按游戏名称分组统计（限制返回前50条）
    @Query(value = "SELECT g.gameName, COUNT(g) FROM GameLog g WHERE g.createdAt BETWEEN :start AND :end GROUP BY g.gameName ORDER BY COUNT(g) DESC LIMIT 50")
    List<Object[]> countByGameNameGroup(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 统计接口：按日期分组统计近N天
    @Query("SELECT FUNCTION('DATE', g.createdAt), COUNT(g) FROM GameLog g WHERE g.createdAt >= :start GROUP BY FUNCTION('DATE', g.createdAt) ORDER BY FUNCTION('DATE', g.createdAt)")
    List<Object[]> countByDateGroup(@Param("start") LocalDateTime start);

    // 最近日志
    List<GameLog> findTop10ByOrderByCreatedAtDesc();

    // 玩家排行榜：按玩家分组统计日志数量，降序排列（限制返回前100条，避免返回过多数据）
    @Query(value = "SELECT g.player, COUNT(g) as cnt FROM GameLog g WHERE g.createdAt BETWEEN :start AND :end GROUP BY g.player ORDER BY cnt DESC LIMIT 100")
    List<Object[]> findPlayerStats(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 时段热力图：按天和小时分组统计（返回过去7天数据）
    @Query(value = "SELECT DATE(created_at) as day, HOUR(created_at) as hour, COUNT(*) as cnt FROM game_log WHERE created_at >= :start GROUP BY DATE(created_at), HOUR(created_at) ORDER BY day, hour", nativeQuery = true)
    List<Object[]> findDailyHourlyStats(@Param("start") LocalDateTime start);

    // 操作类型分布：按action分组统计（限制返回前20条）
    @Query(value = "SELECT g.action, COUNT(g) as cnt FROM GameLog g WHERE g.createdAt BETWEEN :start AND :end GROUP BY g.action ORDER BY cnt DESC LIMIT 20")
    List<Object[]> findActionStats(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 游戏时长统计：计算平均游戏时长
    @Query("SELECT AVG(g.duration) FROM GameLog g WHERE g.duration IS NOT NULL AND g.createdAt BETWEEN :start AND :end")
    Double findAverageDuration(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 总写入次数（用于监控）
    @Query("SELECT COUNT(g) FROM GameLog g")
    long countTotal();
}
