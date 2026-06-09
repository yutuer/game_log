-- ============================================================
-- V1.0.5  同步 id_sequence 初始值（确保 Hibernate 启动时正确读取）
-- 创建时间: 2026-06-10
-- 描述: 在 Hibernate 初始化前将 gen_value 设为 MAX(id)+allocationSize，
--       确保 PooledOptimizer/PooledLoOptimizer 分配的 ID 不与已有记录冲突
-- ============================================================

-- 计算下一可用 ID 段起始值（跳过当前最大 ID 所在的 allocationSize 段）
SET @allocationSize = 50;
SELECT COALESCE(MAX(id), 0) + @allocationSize INTO @nextId FROM game_log;

-- 插入或更新 id_sequence（幂等操作）
INSERT INTO id_sequence (gen_name, gen_value)
VALUES ('game_log_seq', @nextId)
ON DUPLICATE KEY UPDATE gen_value = @nextId;