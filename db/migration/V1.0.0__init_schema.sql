-- ============================================================
-- V1.0.0  初始数据库结构
-- 创建时间: 2026-06-08
-- 描述: 创建 game_log 表，包含所有基础索引
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS game_log DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE game_log;

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
    -- 单列索引
    INDEX idx_game_name (game_name),
    INDEX idx_player (player),
    INDEX idx_play_time (play_time),
    INDEX idx_created_at (created_at),
    INDEX idx_action (action),
    -- 复合索引
    INDEX idx_game_name_play_time (game_name, play_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游戏日志表';
