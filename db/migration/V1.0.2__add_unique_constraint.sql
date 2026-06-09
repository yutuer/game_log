-- ============================================================
-- V1.0.2  添加唯一约束（防重复数据）
-- 创建时间: 2026-06-10
-- 描述: 为 (game_name, player, action, play_time) 添加 UNIQUE 约束，
--       防止相同的游戏日志记录重复入库
-- ============================================================

USE game_log;

-- 添加四元组唯一约束
-- 注意：MySQL 下对 VARCHAR 字段建 UNIQUE 索引需要指定前缀长度
--       (InnoDB 索引前缀最大 3072 字节，utf8mb4 下 4 字节/字符)
--       game_name(100)=400B + player(100)=400B + action(200)=800B + play_time=5B = 1605B < 3072B
ALTER TABLE game_log ADD CONSTRAINT uk_game_player_action_time UNIQUE (game_name(100), player(100), action(200), play_time);

-- 验证约束创建结果
-- SELECT CONSTRAINT_NAME, TABLE_NAME, CONSTRAINT_TYPE
-- FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
-- WHERE TABLE_SCHEMA = 'game_log' AND TABLE_NAME = 'game_log';
