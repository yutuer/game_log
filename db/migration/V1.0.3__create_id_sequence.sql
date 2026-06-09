-- ============================================================
-- V1.0.3  创建 ID 序列表（支持 Hibernate batch INSERT）
-- 创建时间: 2026-06-10
-- 描述: 创建 id_sequence 表，配合 @TableGenerator 实现
--       预分配主键 ID，使 JDBC batch INSERT 生效，
--       解决 IDENTITY 策略下 batch_size 被忽略的问题
-- ============================================================

USE game_log;

-- 创建 ID 序列表
CREATE TABLE IF NOT EXISTS id_sequence (
    gen_name    VARCHAR(100) NOT NULL PRIMARY KEY,
    gen_value   BIGINT       NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始化 game_log 主键序列（从 1 开始）
INSERT INTO id_sequence (gen_name, gen_value) VALUES ('game_log_seq', 1)
ON DUPLICATE KEY UPDATE gen_value = gen_value;