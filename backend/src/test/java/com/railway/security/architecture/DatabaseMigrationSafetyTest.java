package com.railway.security.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

// 历史迁移是可校验的发布资产，修复使用新版本脚本而不是修改已执行文件；回滚还应区分结构与业务数据。
class DatabaseMigrationSafetyTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources", "db");

    @Test
    void baselineMigrationIsNonDestructiveAndDatabaseAgnostic() throws IOException {
        String baseline = normalized(RESOURCES.resolve("migration/V1__baseline_schema.sql"));
        assertFalse(baseline.contains("drop table"));
        assertFalse(baseline.contains("drop database"));
        assertFalse(baseline.contains("create database"));
        assertFalse(baseline.contains("use railway_security_check"));
        assertTrue(baseline.contains("create table sys_user"));
        assertTrue(baseline.contains("create table check_task"));
    }

    @Test
    void developmentSeedContainsOnlyIdentityAuthorizationAndRules() throws IOException {
        assertReferenceSeedIsSafe(
                normalized(RESOURCES.resolve("devdata/R__system_reference_data.sql")));
    }

    @Test
    void offlineCompatibilityScriptsAreNonDestructiveAndContainNoBusinessSeed() throws IOException {
        String schema = normalized(Path.of("..", "sql", "schema.sql"));
        assertFalse(schema.contains("drop table"));
        assertFalse(schema.contains("drop database"));
        assertTrue(schema.contains("create database if not exists railway_security_check_demo"));
        assertReferenceSeedIsSafe(normalized(Path.of("..", "sql", "data.sql")));
    }

    private void assertReferenceSeedIsSafe(String seed) {
        assertTrue(seed.contains("insert ignore into sys_user"));
        assertTrue(seed.contains("insert ignore into sys_config"));
        assertTrue(seed.contains("insert ignore into sys_role"));
        for (String businessTable :
                List.of(
                        "base_police_station",
                        "base_key_unit",
                        "base_important_part",
                        "base_target_jurisdiction",
                        "check_task",
                        "check_record",
                        "check_attachment",
                        "hidden_danger",
                        "remind_record",
                        "coverage_stat",
                        "sys_oper_log",
                        "sys_login_log",
                        "sys_auth_session")) {
            assertFalse(
                    seed.matches("(?s).*insert\\s+(ignore\\s+)?into\\s+" + businessTable + "\\b.*"),
                    () -> "开发种子不得写入业务表: " + businessTable);
        }
    }

    private String normalized(Path path) throws IOException {
        return Files.readString(path).toLowerCase(Locale.ROOT);
    }
}
