-- ============================================================
-- V1.0.4  修复 id_sequence 初始值和 gen_name 不匹配问题
-- 创建时间: 2026-06-10
-- 描述: 1. 修正 gen_name 为 'game_log_seq'（对齐 @TableGenerator）
--        2. 初始值设为当前 game_log 表最大 ID + allocationSize
--           避免 INSERT 时与已有主键冲突
-- ============================================================

USE game_log;

-- 1. 删除旧条目（gen_name 写错的）
DELETE FROM id_sequence WHERE gen_name IN ('game_log_seq', 'game_log_id_seq');

-- 2. 重新插入正确数据：从当前最大 ID + 1 开始分配
INSERT INTO id_sequence (gen_name, gen_value)
SELECT 'game_log_seq', COALESCE(MAX(id), 0) + 1
FROM game_log;