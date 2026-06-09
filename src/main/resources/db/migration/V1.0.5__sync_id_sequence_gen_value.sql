-- ============================================================
-- V1.0.5  同步 id_sequence 初始值（确保 Hibernate 启动时正确读取）
-- 创建时间: 2026-06-10
-- 描述: 在 Hibernate 初始化前将 gen_value 设为 MAX(id)+1，
--       避免 PooledLoOptimizer 缓存旧值导致 Duplicate entry
-- ============================================================

-- 计算下一可用 ID（当前最大 ID + 1）
SELECT COALESCE(MAX(id), 0) + 1 INTO @nextId FROM game_log;

-- 插入或更新 id_sequence（幂等操作）
INSERT INTO id_sequence (gen_name, gen_value)
VALUES ('game_log_seq', @nextId)
ON DUPLICATE KEY UPDATE gen_value = @nextId;