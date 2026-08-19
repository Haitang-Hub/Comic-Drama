package com.comicdrama.workflow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 工作流服务启动期 Schema 自动迁移器。
 * 补齐 token_usage_log 表与实体字段之间的差异，避免"Unknown column"导致用量统计 500。
 */
@Slf4j
@Component
public class StartupSchemaMigrator {

    private final JdbcTemplate jdbcTemplate;

    public StartupSchemaMigrator(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        log.info("[Workflow-SchemaMigrator] 开始检查并补齐数据库结构...");
        try {
            String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            if (dbName == null || dbName.isEmpty()) {
                log.warn("[Workflow-SchemaMigrator] 未检测到当前数据库，跳过迁移");
                return;
            }
            ensureTokenUsageLogColumns(dbName);
            log.info("[Workflow-SchemaMigrator] 数据库结构检查完成");
        } catch (Exception e) {
            log.error("[Workflow-SchemaMigrator] 数据库结构迁移失败: {}", e.getMessage(), e);
        }
    }

    private void ensureTokenUsageLogColumns(String dbName) {
        // 与 TokenUsageLog Entity 对齐。
        // TokenUsageLog 字段:
        //   id(继承) taskId userId step nodeType modelName modelType
        //   promptTokens completionTokens totalTokens imageCount videoDuration
        //   latencyMs status errorMsg inputContent outputContent createTime(继承)
        addColumnIfMissing(dbName, "token_usage_log", "prompt_tokens",
                "INT DEFAULT 0 COMMENT '提示词Token数' AFTER model_type");
        addColumnIfMissing(dbName, "token_usage_log", "completion_tokens",
                "INT DEFAULT 0 COMMENT '生成Token数' AFTER prompt_tokens");
        addColumnIfMissing(dbName, "token_usage_log", "total_tokens",
                "INT DEFAULT 0 COMMENT '总Token数' AFTER completion_tokens");
        addColumnIfMissing(dbName, "token_usage_log", "image_count",
                "INT DEFAULT 0 COMMENT '生成图片数量' AFTER total_tokens");
        addColumnIfMissing(dbName, "token_usage_log", "video_duration",
                "DECIMAL(10,2) DEFAULT NULL COMMENT '生成视频时长（秒）' AFTER image_count");
    }

    private void addColumnIfMissing(String dbName, String table, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, dbName, table, column);
        if (count == null || count == 0) {
            String sql = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition;
            log.warn("[Workflow-SchemaMigrator] 自动补齐列: {}.{} -> {}", table, column, sql);
            jdbcTemplate.execute(sql);
        }
    }
}
