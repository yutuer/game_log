-- ============================================================
-- 游戏日志记录系统 - 数据库初始化脚本
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS game_log
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE game_log;

-- 创建日志表
CREATE TABLE IF NOT EXISTS game_log (
    id          BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    game_name   VARCHAR(100)    NOT NULL                 COMMENT '游戏名称',
    player      VARCHAR(100)    NOT NULL                 COMMENT '玩家标识',
    action      VARCHAR(200)    NOT NULL                 COMMENT '游戏动作/事件',
    detail      VARCHAR(1000)   DEFAULT NULL             COMMENT '详细信息',
    play_time   DATETIME        NOT NULL                 COMMENT '游戏时间',
    created_at  DATETIME        NOT NULL                 COMMENT '记录创建时间',
    PRIMARY KEY (id),
    INDEX idx_game_name (game_name),
    INDEX idx_player (player),
    INDEX idx_play_time (play_time),
    INDEX idx_game_name_play_time (game_name, play_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='游戏日志表';
