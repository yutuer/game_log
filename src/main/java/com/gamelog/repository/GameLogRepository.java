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

    // 统计接口：按游戏名称分组统计
    @Query("SELECT g.gameName, COUNT(g) FROM GameLog g WHERE g.createdAt BETWEEN :start AND :end GROUP BY g.gameName ORDER BY COUNT(g) DESC")
    List<Object[]> countByGameNameGroup(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // 统计接口：按日期分组统计近N天
    @Query("SELECT FUNCTION('DATE', g.createdAt), COUNT(g) FROM GameLog g WHERE g.createdAt >= :start GROUP BY FUNCTION('DATE', g.createdAt) ORDER BY FUNCTION('DATE', g.createdAt)")
    List<Object[]> countByDateGroup(@Param("start") LocalDateTime start);

    // 最近日志
    List<GameLog> findTop10ByOrderByCreatedAtDesc();
}
