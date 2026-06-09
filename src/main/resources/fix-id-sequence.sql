-- 启动时自动修复 id_sequence（执行于 JPA 初始化之前）
-- 修正 V1.0.4 中错误的 gen_name='game_log_id_seq'
DELETE FROM id_sequence WHERE gen_name = 'game_log_id_seq';
DELETE FROM id_sequence WHERE gen_name = 'game_log_seq';
INSERT INTO id_sequence (gen_name, gen_value)
SELECT 'game_log_seq', COALESCE(MAX(id), 0) + 1
FROM game_log;