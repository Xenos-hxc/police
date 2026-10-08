-- Production hardening for databases created before Flyway was introduced.
-- Every DDL change is guarded so this migration also works after a fresh schema.sql import.
DELIMITER $$

CREATE PROCEDURE add_column_if_missing(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
  ) THEN
    SET @ddl = p_ddl;
    PREPARE statement FROM @ddl;
    EXECUTE statement;
    DEALLOCATE PREPARE statement;
  END IF;
END$$

CREATE PROCEDURE add_index_if_missing(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_ddl TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
  ) THEN
    SET @ddl = p_ddl;
    PREPARE statement FROM @ddl;
    EXECUTE statement;
    DEALLOCATE PREPARE statement;
  END IF;
END$$

CREATE PROCEDURE add_fk_if_missing(IN p_table VARCHAR(64), IN p_constraint VARCHAR(64), IN p_ddl TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = p_table
       AND CONSTRAINT_NAME = p_constraint AND CONSTRAINT_TYPE = 'FOREIGN KEY'
  ) THEN
    SET @ddl = p_ddl;
    PREPARE statement FROM @ddl;
    EXECUTE statement;
    DEALLOCATE PREPARE statement;
  END IF;
END$$

DELIMITER ;

CALL add_column_if_missing('sys_user', 'token_version',
  'ALTER TABLE sys_user ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER force_change_password');
CALL add_column_if_missing('check_task', 'version',
  'ALTER TABLE check_task ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER count_coverage');
CALL add_column_if_missing('check_attachment', 'sha256',
  'ALTER TABLE check_attachment ADD COLUMN sha256 CHAR(64) NULL AFTER extension');
CALL add_column_if_missing('check_attachment', 'scan_status',
  'ALTER TABLE check_attachment ADD COLUMN scan_status VARCHAR(30) NOT NULL DEFAULT ''LEGACY_UNVERIFIED'' AFTER sha256');
CALL add_column_if_missing('check_attachment', 'storage_status',
  'ALTER TABLE check_attachment ADD COLUMN storage_status VARCHAR(30) NOT NULL DEFAULT ''ACTIVE'' AFTER scan_status');
CALL add_column_if_missing('check_attachment', 'purged_at',
  'ALTER TABLE check_attachment ADD COLUMN purged_at DATETIME NULL AFTER storage_status');

CREATE TABLE IF NOT EXISTS sys_auth_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, session_id VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL,
  refresh_token_hash CHAR(64) NOT NULL, expires_at DATETIME NOT NULL, revoked_at DATETIME,
  last_used_at DATETIME, client_ip VARCHAR(64), user_agent VARCHAR(500),
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  UNIQUE KEY uk_auth_session_id(session_id), INDEX idx_auth_session_user(user_id, revoked_at),
  INDEX idx_auth_session_expire(expires_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS check_period_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, executor_dept_id BIGINT NOT NULL,
  executor_dept_type VARCHAR(30) NOT NULL, target_type VARCHAR(30) NOT NULL,
  task_year INT NOT NULL, quarter TINYINT NOT NULL, total_count INT NOT NULL,
  required_count INT NOT NULL, coverage_percent INT NOT NULL,
  snapshot_status VARCHAR(20) NOT NULL DEFAULT 'OPEN', initialized_at DATETIME NOT NULL,
  sealed_at DATETIME,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  UNIQUE KEY uk_period_snapshot(executor_dept_id, target_type, task_year, quarter),
  INDEX idx_period_snapshot_period(task_year, quarter, snapshot_status)
) ENGINE=InnoDB;

CALL add_index_if_missing('check_task', 'uk_task_identity',
  'ALTER TABLE check_task ADD UNIQUE KEY uk_task_identity(executor_dept_id,target_type,target_id,task_year,quarter)');

INSERT IGNORE INTO check_period_snapshot
  (executor_dept_id, executor_dept_type, target_type, task_year, quarter,
   total_count, required_count, coverage_percent, snapshot_status, initialized_at, sealed_at,
   create_by, create_time, update_by, update_time, deleted)
SELECT grouped.executor_dept_id, grouped.dept_type, grouped.target_type, grouped.task_year, grouped.quarter,
       grouped.total_count,
       CASE
         WHEN grouped.dept_type = 'STATION' THEN grouped.total_count
         WHEN grouped.target_type = 'STATION' THEN
           LEAST(grouped.total_count, CEIL(grouped.total_count * 0.5 * (MOD(grouped.quarter - 1, 2) + 1))) -
           LEAST(grouped.total_count, CEIL(grouped.total_count * 0.5 * MOD(grouped.quarter - 1, 2)))
         ELSE
           LEAST(grouped.total_count, CEIL(grouped.total_count * 0.25 * grouped.quarter)) -
           LEAST(grouped.total_count, CEIL(grouped.total_count * 0.25 * (grouped.quarter - 1)))
       END,
       CASE WHEN grouped.dept_type = 'STATION' THEN 100
            WHEN grouped.target_type = 'STATION' THEN 50 ELSE 25 END,
       CASE WHEN grouped.period_end < NOW() THEN 'SEALED' ELSE 'OPEN' END,
       grouped.first_created,
       CASE WHEN grouped.period_end < NOW() THEN grouped.period_end ELSE NULL END,
       0, NOW(), 0, NOW(), 0
FROM (
  SELECT task.executor_dept_id, dept.dept_type, task.target_type, task.task_year, task.quarter,
         COUNT(*) total_count, MIN(task.create_time) first_created,
         STR_TO_DATE(CONCAT(task.task_year, '-', task.quarter * 3, '-01 23:59:59'), '%Y-%m-%d %H:%i:%s')
           + INTERVAL 1 MONTH - INTERVAL 1 DAY AS period_end
    FROM check_task task JOIN sys_dept dept ON dept.id = task.executor_dept_id
   WHERE task.deleted = 0 AND task.check_type = 'INTERNAL_SECURITY'
   GROUP BY task.executor_dept_id, dept.dept_type, task.target_type, task.task_year, task.quarter
) grouped;

UPDATE sys_user SET force_change_password=1 WHERE force_change_password<>1;
UPDATE check_attachment SET scan_status='LEGACY_UNVERIFIED'
 WHERE deleted=0 AND (scan_status IS NULL OR scan_status='PENDING');
UPDATE check_attachment SET storage_status='DELETED'
 WHERE deleted=1 AND storage_status='ACTIVE';

CALL add_fk_if_missing('sys_user', 'fk_user_dept',
  'ALTER TABLE sys_user ADD CONSTRAINT fk_user_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id)');
CALL add_fk_if_missing('sys_auth_session', 'fk_auth_session_user',
  'ALTER TABLE sys_auth_session ADD CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES sys_user(id)');
CALL add_fk_if_missing('sys_user_role', 'fk_user_role_user',
  'ALTER TABLE sys_user_role ADD CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id)');
CALL add_fk_if_missing('sys_user_role', 'fk_user_role_role',
  'ALTER TABLE sys_user_role ADD CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id)');
CALL add_fk_if_missing('sys_role_menu', 'fk_role_menu_role',
  'ALTER TABLE sys_role_menu ADD CONSTRAINT fk_role_menu_role FOREIGN KEY (role_id) REFERENCES sys_role(id)');
CALL add_fk_if_missing('sys_role_menu', 'fk_role_menu_menu',
  'ALTER TABLE sys_role_menu ADD CONSTRAINT fk_role_menu_menu FOREIGN KEY (menu_id) REFERENCES sys_menu(id)');
CALL add_fk_if_missing('base_police_station', 'fk_station_dept',
  'ALTER TABLE base_police_station ADD CONSTRAINT fk_station_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id)');
CALL add_fk_if_missing('base_target_jurisdiction', 'fk_jurisdiction_station',
  'ALTER TABLE base_target_jurisdiction ADD CONSTRAINT fk_jurisdiction_station FOREIGN KEY (station_dept_id) REFERENCES sys_dept(id)');
CALL add_fk_if_missing('check_period_snapshot', 'fk_snapshot_executor',
  'ALTER TABLE check_period_snapshot ADD CONSTRAINT fk_snapshot_executor FOREIGN KEY (executor_dept_id) REFERENCES sys_dept(id)');
CALL add_fk_if_missing('check_task', 'fk_task_initiator',
  'ALTER TABLE check_task ADD CONSTRAINT fk_task_initiator FOREIGN KEY (initiator_dept_id) REFERENCES sys_dept(id)');
CALL add_fk_if_missing('check_task', 'fk_task_executor',
  'ALTER TABLE check_task ADD CONSTRAINT fk_task_executor FOREIGN KEY (executor_dept_id) REFERENCES sys_dept(id)');
CALL add_fk_if_missing('check_record', 'fk_record_task',
  'ALTER TABLE check_record ADD CONSTRAINT fk_record_task FOREIGN KEY (task_id) REFERENCES check_task(id)');
CALL add_fk_if_missing('check_attachment', 'fk_attachment_task',
  'ALTER TABLE check_attachment ADD CONSTRAINT fk_attachment_task FOREIGN KEY (task_id) REFERENCES check_task(id)');
CALL add_fk_if_missing('check_attachment', 'fk_attachment_record',
  'ALTER TABLE check_attachment ADD CONSTRAINT fk_attachment_record FOREIGN KEY (record_id) REFERENCES check_record(id)');
CALL add_fk_if_missing('hidden_danger', 'fk_danger_task',
  'ALTER TABLE hidden_danger ADD CONSTRAINT fk_danger_task FOREIGN KEY (task_id) REFERENCES check_task(id)');
CALL add_fk_if_missing('remind_record', 'fk_remind_task',
  'ALTER TABLE remind_record ADD CONSTRAINT fk_remind_task FOREIGN KEY (task_id) REFERENCES check_task(id)');
CALL add_fk_if_missing('remind_record', 'fk_remind_user',
  'ALTER TABLE remind_record ADD CONSTRAINT fk_remind_user FOREIGN KEY (receiver_user_id) REFERENCES sys_user(id)');
CALL add_fk_if_missing('remind_record', 'fk_remind_dept',
  'ALTER TABLE remind_record ADD CONSTRAINT fk_remind_dept FOREIGN KEY (receiver_dept_id) REFERENCES sys_dept(id)');
CALL add_fk_if_missing('coverage_stat', 'fk_coverage_dept',
  'ALTER TABLE coverage_stat ADD CONSTRAINT fk_coverage_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id)');

INSERT INTO sys_config(config_name, config_key, config_value, value_type, system_flag, remark)
VALUES ('已删除文件保留天数', 'upload.deleted.retention.days', '30', 'NUMBER', 1,
        '逻辑删除后保留，超过期限仅清理磁盘文件，数据库追溯记录不删除')
ON DUPLICATE KEY UPDATE config_name=VALUES(config_name), remark=VALUES(remark);

DROP PROCEDURE add_column_if_missing;
DROP PROCEDURE add_index_if_missing;
DROP PROCEDURE add_fk_if_missing;
