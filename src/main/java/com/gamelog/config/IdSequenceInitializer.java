package com.gamelog.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 在 JPA/Tomcat 启动前，用原生 JDBC 初始化 id_sequence
 * 解决 @TableGenerator 在启动时读到错误 gen_value 导致 Duplicate entry 的问题
 */
@Slf4j
@Component
public class IdSequenceInitializer implements InitializingBean {

    private final JdbcTemplate jdbcTemplate;

    public IdSequenceInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void afterPropertiesSet() {
        try {
            // 1. 获取当前 game_log 表最大 ID
            Long maxId = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(id), 0) FROM game_log", Long.class);
            long startValue = (maxId == null ? 0L : maxId) + 1;

            // 2. 删除错误的行（V1.0.4 遗留）
            jdbcTemplate.update("DELETE FROM id_sequence WHERE gen_name = 'game_log_id_seq'");

            // 3. UPSERT 正确的行
            int updated = jdbcTemplate.update(
                    "UPDATE id_sequence SET gen_value = ? WHERE gen_name = 'game_log_seq'",
                    startValue);
            if (updated == 0) {
                jdbcTemplate.update(
                        "INSERT INTO id_sequence (gen_name, gen_value) VALUES ('game_log_seq', ?)",
                        startValue);
            }

            log.info("[IdSequenceInitializer] id_sequence 已初始化: gen_value={} (max_id={})",
                    startValue, maxId);
        } catch (Exception e) {
            log.error("[IdSequenceInitializer] 初始化失败", e);
        }
    }
}