package com.comicdrama.task.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 启动期 Schema 自动迁移器（Task 服务侧）。
 * 检查 sys_user.role 列是否存在，缺失则自动 ALTER TABLE 补齐并回填 admin 角色，
 * 避免 RBAC 5→1 表简化后因实际数据库缺列导致的 Unknown column 启动失败。
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
        log.info("[Task-SchemaMigrator] 开始检查并补齐 Task 数据库结构...");
        try {
            String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            if (dbName == null || dbName.isEmpty()) {
                log.warn("[Task-SchemaMigrator] 未检测到当前数据库，跳过迁移");
                return;
            }

            ensureSysUserColumns(dbName);
            backfillAdminRole();
            dropStaleRbacTables(dbName);

            log.info("[Task-SchemaMigrator] 数据库结构检查完成");
        } catch (Exception e) {
            log.error("[Task-SchemaMigrator] 数据库结构迁移失败: {}", e.getMessage(), e);
        }
    }

    private void ensureSysUserColumns(String dbName) {
        addColumnIfMissing(dbName, "sys_user", "role",
                "VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER' AFTER status");
    }

    private void backfillAdminRole() {
        // 给 username=admin 的账号回填 ADMIN 角色（若仍为默认 USER 或空）
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username='admin' AND (role IS NULL OR role='')",
                Integer.class);
        if (count != null && count > 0) {
            jdbcTemplate.execute("UPDATE sys_user SET role='ADMIN' WHERE username='admin' AND (role IS NULL OR role='')");
            log.warn("[Task-SchemaMigrator] 已回填 admin 账号角色为 ADMIN");
        }
        Integer count2 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username='admin' AND role='USER'",
                Integer.class);
        if (count2 != null && count2 > 0) {
            jdbcTemplate.execute("UPDATE sys_user SET role='ADMIN' WHERE username='admin' AND role='USER'");
            log.warn("[Task-SchemaMigrator] 已修正 admin 账号角色从 USER 到 ADMIN");
        }
        // 其它账号为空时补 USER
        Integer others = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE role IS NULL OR role=''",
                Integer.class);
        if (others != null && others > 0) {
            jdbcTemplate.execute("UPDATE sys_user SET role='USER' WHERE role IS NULL OR role=''");
            log.warn("[Task-SchemaMigrator] 已为 {} 个账号回填默认角色 USER", others);
        }
    }

    private void dropStaleRbacTables(String dbName) {
        String[] tables = {"sys_role_permission", "sys_user_role", "sys_permission", "sys_role"};
        for (String t : tables) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                    Integer.class, dbName, t);
            if (exists != null && exists > 0) {
                jdbcTemplate.execute("DROP TABLE IF EXISTS " + t);
                log.warn("[Task-SchemaMigrator] 已清理冗余表 {}", t);
            }
        }
    }

    private void addColumnIfMissing(String dbName, String table, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, dbName, table, column);
        if (count == null || count == 0) {
            String sql = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition;
            log.warn("[Task-SchemaMigrator] 自动补齐列: {}.{} -> {}", table, column, sql);
            jdbcTemplate.execute(sql);
        }
    }
}
