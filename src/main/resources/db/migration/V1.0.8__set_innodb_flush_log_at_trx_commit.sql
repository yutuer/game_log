-- ============================================================
-- V1.0.8  设置 InnoDB 事务日志刷盘策略
-- 创建时间: 2026-06-11
-- 描述: 将 innodb_flush_log_at_trx_commit 设为 2，减少 commit
--       时的磁盘 fsync 开销，显著提升批量写入性能。
--       值为 2 时：每秒刷一次磁盘，崩溃时最多丢 1 秒数据。
--
-- ⚠️ 此设置重启 MySQL 后失效，如需持久化请在 my.ini/my.cnf
--    的 [mysqld] 段添加：innodb_flush_log_at_trx_commit = 2
-- ============================================================

SET GLOBAL innodb_flush_log_at_trx_commit = 2;
