package com.gamelog.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 游戏日志实体（纯 POJO，无 ORM 注解）
 * 表结构由 Flyway migration 管理
 */
@Data
public class GameLog {
    private Long id;
    private String gameName;
    private String player;
    private String action;
    private String detail;
    private LocalDateTime playTime;
    private Integer duration;
    private LocalDateTime createdAt;
}
