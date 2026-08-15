package com.comicdrama.resource.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 启动期 Schema 自动迁移器。
 * 检查 comic_work / comic_work_timeline / scene_video 等表的必要列/表是否存在，
 * 缺失则用 ALTER TABLE / CREATE TABLE IF NOT EXISTS 自动补齐，
 * 避免"Unknown column"导致的 500 错误。
 *
 * 注意：该组件只做结构补齐，不清空数据；数据清空/重写请手动执行 SQL 脚本。
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
        log.info("[SchemaMigrator] 开始检查并补齐数据库结构...");
        try {
            String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            if (dbName == null || dbName.isEmpty()) {
                log.warn("[SchemaMigrator] 未检测到当前数据库，跳过迁移");
                return;
            }

            ensureComicWorkColumns(dbName);
            ensureComicWorkTimelineTable(dbName);
            ensureSceneVideoColumns(dbName);

            log.info("[SchemaMigrator] 数据库结构检查完成");
        } catch (Exception e) {
            log.error("[SchemaMigrator] 数据库结构迁移失败: {}", e.getMessage(), e);
        }
    }

    private void ensureComicWorkColumns(String dbName) {
        String table = "comic_work";
        addColumnIfMissing(dbName, table, "zip_url",
                "VARCHAR(512) DEFAULT NULL COMMENT '完整成片ZIP包URL（下载用）' AFTER video_url");
        addColumnIfMissing(dbName, table, "segment_count",
                "INT DEFAULT 0 COMMENT '合并的视频段数' AFTER zip_url");
        addColumnIfMissing(dbName, table, "merged_from",
                "VARCHAR(1024) DEFAULT NULL COMMENT '合并来源的场景视频ID列表（逗号分隔）' AFTER segment_count");
        addColumnIfMissing(dbName, table, "aspect_ratio",
                "VARCHAR(16) DEFAULT NULL COMMENT '画面比例' AFTER resolution");
        addColumnIfMissing(dbName, table, "file_size",
                "BIGINT DEFAULT 0 COMMENT '成片文件大小（字节）' AFTER aspect_ratio");
        addColumnIfMissing(dbName, table, "share_token",
                "VARCHAR(128) DEFAULT NULL COMMENT '分享令牌' AFTER like_count");
        addColumnIfMissing(dbName, table, "share_expire",
                "DATETIME DEFAULT NULL COMMENT '分享过期时间' AFTER share_token");
        addColumnIfMissing(dbName, table, "publish_time",
                "DATETIME DEFAULT NULL COMMENT '发布时间' AFTER share_expire");
    }

    private void ensureComicWorkTimelineTable(String dbName) {
        String exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                Integer.class, dbName, "comic_work_timeline") > 0 ? "1" : "0";
        if (!"1".equals(exists)) {
            String create = "CREATE TABLE comic_work_timeline (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID'," +
                    "work_id BIGINT NOT NULL COMMENT '作品ID'," +
                    "scene_group_id BIGINT DEFAULT NULL COMMENT '场景组ID'," +
                    "storyboard_id BIGINT DEFAULT NULL COMMENT '分镜ID'," +
                    "video_url VARCHAR(1024) DEFAULT NULL COMMENT '场景视频URL'," +
                    "order_index INT DEFAULT 0 COMMENT '播放顺序（从1开始）'," +
                    "duration INT DEFAULT 0 COMMENT '片段时长（秒）'," +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_work_id (work_id)," +
                    "KEY idx_scene_group_id (scene_group_id)," +
                    "KEY idx_storyboard_id (storyboard_id)," +
                    "KEY idx_order_index (order_index)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品时间线表（场景组分镜回放顺序）'";
            jdbcTemplate.execute(create);
            log.warn("[SchemaMigrator] 已自动创建 comic_work_timeline 表");
        }
    }

    private void ensureSceneVideoColumns(String dbName) {
        addColumnIfMissing(dbName, "scene_video", "storyboard_seq_range",
                "VARCHAR(64) DEFAULT NULL COMMENT '分镜序号区间（如 1-5）' AFTER storyboard_ids");
    }

    private void addColumnIfMissing(String dbName, String table, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, dbName, table, column);
        if (count == null || count == 0) {
            String sql = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition;
            log.warn("[SchemaMigrator] 自动补齐列: {}.{} -> {}", table, column, sql);
            jdbcTemplate.execute(sql);
        }
    }
}
