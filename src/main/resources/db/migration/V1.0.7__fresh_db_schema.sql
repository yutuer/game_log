-- ============================================================
-- V1.0.7  完整数据库初始化（新库场景）
-- 创建时间: 2026-06-10
-- 描述: 汇总 V1.0.0~V1.0.6 的所有有效 DDL，用于在新库上
--       一步完成建表+索引+约束。
--       已有库上执行是幂等的（CREATE TABLE IF NOT EXISTS）。
--
-- 背景：旧迁移文件 V1.0.0~V1.0.4 位于项目根目录 db/migration/
--       下，不在 classpath 中，Spring Boot 打包后不会包含。
--       V1.0.5~V1.0.6 虽在 classpath 中，但依赖 id_sequence 表
--       （已废弃），新库上会失败。
--       因此 baseline 提升至 1.0.6，由 V1.0.7 统一处理建库。
-- ============================================================

-- 创建游戏日志表
CREATE TABLE IF NOT EXISTS game_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    game_name  VARCHAR(100) NOT NULL                COMMENT '游戏名称',
    player     VARCHAR(100) NOT NULL                COMMENT '玩家',
    action     VARCHAR(200) NOT NULL                COMMENT '操作类型',
    detail     VARCHAR(1000)                       COMMENT '详细信息',
    play_time  DATETIME     NOT NULL                COMMENT '游戏时间',
    duration   INT                                 COMMENT '游戏时长（分钟）',
    created_at DATETIME     NOT NULL                COMMENT '记录创建时间',
    PRIMARY KEY (id),
    -- 单列索引（来自 V1.0.0）
    INDEX idx_game_name (game_name),
    INDEX idx_player (player),
    INDEX idx_play_time (play_time),
    INDEX idx_created_at (created_at),
    INDEX idx_action (action),
    -- 复合索引（来自 V1.0.1）
    INDEX idx_player_created (player, created_at),
    INDEX idx_game_name_created (game_name, created_at),
    INDEX idx_action_created (action, created_at),
    -- 原复合索引（来自 V1.0.0）
    INDEX idx_game_name_play_time (game_name, play_time),
    -- 唯一约束（来自 V1.0.2）
    UNIQUE KEY uk_game_player_action_time (game_name(100), player(100), action(200), play_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游戏日志表';
