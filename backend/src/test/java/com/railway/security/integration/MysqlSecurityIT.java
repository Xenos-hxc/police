package com.railway.security.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railway.security.persistence.mapper.ScanOutboxMapper;
import com.railway.security.shared.scheduling.MysqlJobLock;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "management.server.port=-1",
            "app.redis.enabled=false",
            "spring.task.scheduling.enabled=false",
            "app.upload.antivirus.enabled=false",
            "app.jwt.secret=integration-test-secret-integration-test-secret-20260930"
        })
class MysqlSecurityIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.41");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private TransactionTemplate transactions;
    @Autowired private MysqlJobLock jobLock;
    @Autowired private ScanOutboxMapper scanOutbox;

    @Test
    void scanOutboxParticipatesInTheUploadTransaction() {
        long attachmentId = 9_000_000_001L;
        assertThrows(
                IllegalStateException.class,
                () ->
                        transactions.execute(
                                status -> {
                                    scanOutbox.enqueue(attachmentId);
                                    throw new IllegalStateException("simulate upload failure");
                                }));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM attachment_scan_outbox WHERE attachment_id=?",
                        Integer.class,
                        attachmentId));
    }

    @Test
    void scanOutboxClaimAndConfirmAreIdempotent() {
        long attachmentId = 9_000_000_002L;
        scanOutbox.enqueue(attachmentId);
        assertEquals(1, scanOutbox.claim(attachmentId));
        assertEquals(0, scanOutbox.claim(attachmentId));
        assertEquals(1, scanOutbox.sent(attachmentId));
        assertEquals(0, scanOutbox.sent(attachmentId));
        assertEquals(1, scanOutbox.reconcileFinished());
        assertEquals(
                "DONE",
                jdbc.queryForObject(
                        "SELECT status FROM attachment_scan_outbox WHERE attachment_id=?",
                        String.class,
                        attachmentId));
    }

    @Test
    void flywayCreatesOnlyReferenceDataAndEnforcesUniqueKeys(@Autowired Flyway flyway) {
        // Compare resolved versioned migrations, so new migrations cannot silently
        // leave this integration assertion pinned to an obsolete schema version.
        long versionedMigrations =
                java.util.Arrays.stream(flyway.info().all())
                        .filter(migration -> migration.getVersion() != null)
                        .count();
        assertEquals(
                versionedMigrations,
                jdbc.queryForObject(
                                "SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL AND success=1",
                                Integer.class)
                        .longValue());
        assertEquals(27, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM check_task", Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject("SELECT COUNT(*) FROM check_period_snapshot", Integer.class));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "INSERT INTO sys_config(config_name,config_key,config_value,value_type) VALUES ('duplicate','captcha.enabled','true','BOOLEAN')"));
    }

    @Test
    void transactionRollbackDoesNotLeaveReferenceData() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        transactions.executeWithoutResult(
                                status -> {
                                    jdbc.update(
                                            "INSERT INTO sys_config(config_name,config_key,config_value,value_type) VALUES ('rollback','p1.rollback.test','true','BOOLEAN')");
                                    throw new IllegalStateException("rollback");
                                }));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM sys_config WHERE config_key='p1.rollback.test'",
                        Integer.class));
    }

    @Test
    void loginAndRoleBoundariesUseRealDatabase() throws Exception {
        String stationToken = accessToken(login("station01"));
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + stationToken))
                .andExpect(status().isForbidden());
        String adminToken = accessToken(login("admin"));
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/files/123/preview")).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenReplayAllowsAtMostOneRotation() throws Exception {
        Cookie refreshCookie = login("admin").getCookie("RAILWAY_REFRESH_TOKEN");
        assertTrue(refreshCookie != null && !refreshCookie.getValue().isBlank());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> refresh =
                    () ->
                            mvc.perform(post("/api/auth/refresh-token").cookie(refreshCookie))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
            List<Integer> results =
                    executor.invokeAll(List.of(refresh, refresh)).stream()
                            .map(
                                    future -> {
                                        try {
                                            return future.get();
                                        } catch (Exception ex) {
                                            throw new IllegalStateException(ex);
                                        }
                                    })
                            .toList();
            assertEquals(1, results.stream().filter(code -> code == 200).count());
            assertEquals(1, results.stream().filter(code -> code == 401).count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void pendingFilesCannotBeDownloadedAndTicketsCannotBeReplayed(@TempDir Path directory)
            throws Exception {
        String taskNo = "P1-IT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        jdbc.update(
                """
                INSERT INTO check_task(task_no,task_name,check_type,initiator_dept_id,
                  executor_dept_id,target_id,target_type,target_name,task_year,quarter,start_date,deadline)
                VALUES (?,'Integration test','ROUTINE',1,101,999999,'KEY_UNIT','Fixture',2026,3,
                  '2026-09-01','2026-12-01')
                """,
                taskNo);
        Long taskId =
                jdbc.queryForObject(
                        "SELECT id FROM check_task WHERE task_no=?", Long.class, taskNo);
        Path fixture = directory.resolve("fixture.txt");
        Files.writeString(fixture, "safe fixture");
        jdbc.update(
                """
                INSERT INTO check_attachment(task_id,attachment_type,original_name,stored_name,
                  storage_path,file_size,extension,scan_status,storage_status)
                VALUES (?,'RECORD','fixture.txt','fixture.txt',?,12,'txt','PENDING','QUARANTINED')
                """,
                taskId,
                fixture.toString());
        Long fileId =
                jdbc.queryForObject(
                        "SELECT id FROM check_attachment WHERE task_id=?", Long.class, taskId);
        try {
            String token = accessToken(login("station01"));
            String otherStation = accessToken(login("cddcz"));
            mvc.perform(
                            get("/api/files/{id}", fileId)
                                    .header("Authorization", "Bearer " + otherStation))
                    .andExpect(status().isForbidden());
            mvc.perform(
                            post("/api/files/{id}/download-ticket", fileId)
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict());
            jdbc.update(
                    "UPDATE check_attachment SET scan_status='CLEAN',storage_status='ACTIVE' WHERE id=?",
                    fileId);
            var ticketResponse =
                    mvc.perform(
                                    post("/api/files/{id}/download-ticket", fileId)
                                            .header("Authorization", "Bearer " + token))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse();
            String downloadUrl =
                    json.readTree(ticketResponse.getContentAsString())
                            .path("data")
                            .path("downloadUrl")
                            .asText();
            mvc.perform(get(downloadUrl)).andExpect(status().isOk());
            mvc.perform(get(downloadUrl)).andExpect(status().isForbidden());
        } finally {
            jdbc.update("DELETE FROM check_attachment WHERE id=?", fileId);
            jdbc.update("DELETE FROM check_task WHERE id=?", taskId);
        }
    }

    @Test
    void onlyOneNodeCanEnterScheduledJob() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var first =
                    executor.submit(
                            () ->
                                    jobLock.runIfLeader(
                                            "railway:p1-test-lock",
                                            () -> {
                                                entered.countDown();
                                                try {
                                                    if (!release.await(10, TimeUnit.SECONDS))
                                                        throw new IllegalStateException("timeout");
                                                } catch (InterruptedException exception) {
                                                    Thread.currentThread().interrupt();
                                                    throw new IllegalStateException(exception);
                                                }
                                            }));
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            jobLock.runIfLeader("railway:p1-test-lock", () -> fail("second instance entered job"));
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private org.springframework.mock.web.MockHttpServletResponse login(String username)
            throws Exception {
        return mvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content(
                                        "{\"username\":\""
                                                + username
                                                + "\",\"password\":\"Demo-Only-Change-Me!2026\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
    }

    private String accessToken(org.springframework.mock.web.MockHttpServletResponse response)
            throws Exception {
        JsonNode body = json.readTree(response.getContentAsString());
        return body.path("data").path("accessToken").asText();
    }
}
