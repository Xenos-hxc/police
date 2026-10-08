-- Canonical baseline for a new, empty schema. Database creation belongs to the
-- runtime/deployment environment; Flyway owns every table from this point on.
SET NAMES utf8mb4;

CREATE TABLE sys_dept (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NOT NULL DEFAULT 0,
  ancestors VARCHAR(500) NOT NULL DEFAULT '0',
  dept_name VARCHAR(100) NOT NULL,
  dept_type VARCHAR(30) NOT NULL COMMENT 'BUREAU/STATION',
  leader VARCHAR(50), phone VARCHAR(30), address VARCHAR(255), jurisdiction VARCHAR(500),
  sort_no INT NOT NULL DEFAULT 0, status TINYINT NOT NULL DEFAULT 1, archived TINYINT NOT NULL DEFAULT 0,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  INDEX idx_dept_parent(parent_id), INDEX idx_dept_type(dept_type), INDEX idx_dept_status(status, deleted)
) ENGINE=InnoDB;

CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dept_id BIGINT NOT NULL, username VARCHAR(50) NOT NULL, password VARCHAR(100) NOT NULL,
  real_name VARCHAR(50) NOT NULL, phone VARCHAR(30), status TINYINT NOT NULL DEFAULT 1,
  data_scope VARCHAR(30) NOT NULL DEFAULT 'DEPT', force_change_password TINYINT NOT NULL DEFAULT 0,
  token_version INT NOT NULL DEFAULT 0,
  last_login_time DATETIME, last_login_ip VARCHAR(64),
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  UNIQUE KEY uk_user_username(username), INDEX idx_user_dept(dept_id)
) ENGINE=InnoDB;

CREATE TABLE sys_auth_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, session_id VARCHAR(64) NOT NULL, user_id BIGINT NOT NULL,
  refresh_token_hash CHAR(64) NOT NULL, expires_at DATETIME NOT NULL, revoked_at DATETIME,
  last_used_at DATETIME, client_ip VARCHAR(64), user_agent VARCHAR(500),
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  UNIQUE KEY uk_auth_session_id(session_id), INDEX idx_auth_session_user(user_id, revoked_at),
  INDEX idx_auth_session_expire(expires_at)
) ENGINE=InnoDB;

CREATE TABLE sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, role_name VARCHAR(50) NOT NULL, role_code VARCHAR(50) NOT NULL,
  data_scope VARCHAR(30) NOT NULL DEFAULT 'DEPT', status TINYINT NOT NULL DEFAULT 1,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  UNIQUE KEY uk_role_code(role_code)
) ENGINE=InnoDB;

CREATE TABLE sys_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, parent_id BIGINT NOT NULL DEFAULT 0, menu_name VARCHAR(50) NOT NULL,
  menu_type CHAR(1) NOT NULL COMMENT 'M目录 C菜单 F按钮', path VARCHAR(200), component VARCHAR(255),
  permission VARCHAR(100), icon VARCHAR(50), sort_no INT DEFAULT 0, visible TINYINT DEFAULT 1, status TINYINT DEFAULT 1,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500), INDEX idx_menu_parent(parent_id)
) ENGINE=InnoDB;

CREATE TABLE sys_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, role_id BIGINT NOT NULL,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  UNIQUE KEY uk_user_role(user_id, role_id)
) ENGINE=InnoDB;

CREATE TABLE sys_role_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, role_id BIGINT NOT NULL, menu_id BIGINT NOT NULL,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  UNIQUE KEY uk_role_menu(role_id, menu_id)
) ENGINE=InnoDB;

CREATE TABLE base_police_station (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, dept_id BIGINT NOT NULL, station_code VARCHAR(30) NOT NULL,
  station_name VARCHAR(100) NOT NULL, leader VARCHAR(50), phone VARCHAR(30), address VARCHAR(255),
  jurisdiction VARCHAR(500), status TINYINT NOT NULL DEFAULT 1, archived TINYINT NOT NULL DEFAULT 0,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  UNIQUE KEY uk_station_code(station_code), INDEX idx_station_dept(dept_id)
) ENGINE=InnoDB;

CREATE TABLE base_key_unit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  unit_code VARCHAR(30) NOT NULL, unit_name VARCHAR(200) NOT NULL, unit_type VARCHAR(100),
  established_date DATE, leader VARCHAR(50), contact_person VARCHAR(50), phone VARCHAR(30), address VARCHAR(500),
  bureau_name VARCHAR(100), jurisdiction_text VARCHAR(500),
  last_check_time DATETIME, last_check_result VARCHAR(100), status TINYINT NOT NULL DEFAULT 1, archived TINYINT NOT NULL DEFAULT 0,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  UNIQUE KEY uk_unit_code(unit_code), INDEX idx_unit_name(unit_name), INDEX idx_unit_status(status, archived, deleted)
) ENGINE=InnoDB;

CREATE TABLE base_important_part (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  part_code VARCHAR(30) NOT NULL, part_name VARCHAR(255) NOT NULL, part_type VARCHAR(100),
  established_date DATE, removed_date DATE, guard_status VARCHAR(100), length_description VARCHAR(255),
  railway_line VARCHAR(100), kilometer_mark VARCHAR(255), location VARCHAR(1000),
  responsible_unit VARCHAR(200), workshop VARCHAR(200), bureau_name VARCHAR(100), jurisdiction_text VARCHAR(500),
  last_check_time DATETIME, last_check_result VARCHAR(100),
  status TINYINT NOT NULL DEFAULT 1, archived TINYINT NOT NULL DEFAULT 0,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  UNIQUE KEY uk_part_code(part_code), INDEX idx_part_name(part_name), INDEX idx_part_status(status, archived, deleted)
) ENGINE=InnoDB;

CREATE TABLE base_target_jurisdiction (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  station_dept_id BIGINT NOT NULL,
  target_type VARCHAR(30) NOT NULL COMMENT 'KEY_UNIT/IMPORTANT_PART',
  target_id BIGINT NOT NULL,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  UNIQUE KEY uk_station_target(station_dept_id, target_type, target_id),
  INDEX idx_jurisdiction_target(target_type, target_id),
  INDEX idx_jurisdiction_station(station_dept_id, target_type)
) ENGINE=InnoDB;

CREATE TABLE check_period_snapshot (
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

CREATE TABLE check_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, task_no VARCHAR(40) NOT NULL, task_name VARCHAR(200) NOT NULL,
  check_type VARCHAR(40) NOT NULL, task_category VARCHAR(30) NOT NULL DEFAULT 'COVERAGE',
  creation_mode VARCHAR(20) NOT NULL DEFAULT 'SYSTEM', initiator_dept_id BIGINT NOT NULL,
  executor_dept_id BIGINT NOT NULL, target_id BIGINT NOT NULL, target_type VARCHAR(30) NOT NULL,
  target_name VARCHAR(150) NOT NULL, task_year INT NOT NULL, quarter TINYINT, half_year TINYINT,
  start_date DATE NOT NULL, deadline DATETIME NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  overdue TINYINT NOT NULL DEFAULT 0, overdue_submitted TINYINT NOT NULL DEFAULT 0,
  count_coverage TINYINT NOT NULL DEFAULT 1, version INT NOT NULL DEFAULT 0,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  UNIQUE KEY uk_task_no(task_no),
  UNIQUE KEY uk_task_identity(executor_dept_id, target_type, target_id, task_year, quarter),
  INDEX idx_task_period(task_year, quarter, half_year), INDEX idx_task_type(check_type),
  INDEX idx_task_executor(executor_dept_id), INDEX idx_task_target(target_id, target_type),
  INDEX idx_task_status(status), INDEX idx_task_deadline(deadline)
) ENGINE=InnoDB;

CREATE TABLE check_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, task_id BIGINT NOT NULL, check_time DATETIME,
  inspectors VARCHAR(255), has_danger TINYINT NOT NULL DEFAULT 0,
  rectification_type VARCHAR(30), danger_detail TEXT, rectification_deadline DATE,
  submitted_by BIGINT, submitted_time DATETIME, complete TINYINT NOT NULL DEFAULT 0,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500), UNIQUE KEY uk_record_task(task_id)
) ENGINE=InnoDB;

CREATE TABLE check_attachment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, task_id BIGINT NOT NULL, record_id BIGINT,
  attachment_type VARCHAR(30) NOT NULL, original_name VARCHAR(255) NOT NULL, stored_name VARCHAR(255) NOT NULL,
  storage_path VARCHAR(500) NOT NULL, file_size BIGINT NOT NULL, content_type VARCHAR(100), extension VARCHAR(20),
  sha256 CHAR(64), scan_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  storage_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', purged_at DATETIME,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  INDEX idx_attachment_task(task_id)
) ENGINE=InnoDB;

CREATE TABLE hidden_danger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  danger_no VARCHAR(60) NOT NULL, task_id BIGINT NOT NULL, task_no VARCHAR(60) NOT NULL,
  task_year INT NOT NULL, quarter TINYINT NOT NULL, executor_dept_id BIGINT NOT NULL,
  target_id BIGINT NOT NULL, target_type VARCHAR(30) NOT NULL, target_name VARCHAR(255) NOT NULL,
  danger_detail TEXT NOT NULL, rectification_type VARCHAR(30) NOT NULL,
  rectification_deadline DATE, status VARCHAR(30) NOT NULL,
  rectification_check_time DATETIME, rectification_inspectors VARCHAR(255),
  rectification_remark VARCHAR(1000), rectification_submitted_by BIGINT,
  rectification_submitted_time DATETIME,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  UNIQUE KEY uk_danger_no(danger_no), UNIQUE KEY uk_danger_task(task_id),
  INDEX idx_danger_scope(executor_dept_id, target_type, task_year, quarter),
  INDEX idx_danger_status(status, rectification_deadline), INDEX idx_danger_task_no(task_no)
) ENGINE=InnoDB;



CREATE TABLE remind_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, task_id BIGINT NOT NULL, receiver_user_id BIGINT,
  receiver_dept_id BIGINT NOT NULL, remind_type VARCHAR(30) NOT NULL, remind_content VARCHAR(500) NOT NULL,
  remind_time DATETIME NOT NULL, read_flag TINYINT NOT NULL DEFAULT 0,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500), INDEX idx_remind_task(task_id)
) ENGINE=InnoDB;

CREATE TABLE coverage_stat (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, dept_id BIGINT NOT NULL, check_type VARCHAR(40) NOT NULL,
  task_year INT NOT NULL, quarter TINYINT, half_year TINYINT, required_count INT NOT NULL,
  covered_count INT NOT NULL, submitted_count INT NOT NULL, overdue_count INT NOT NULL,
  calculated_time DATETIME NOT NULL,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500),
  INDEX idx_stat_scope(dept_id, check_type, task_year, quarter, half_year)
) ENGINE=InnoDB;

CREATE TABLE sys_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, config_name VARCHAR(100) NOT NULL, config_key VARCHAR(100) NOT NULL,
  config_value VARCHAR(1000) NOT NULL, value_type VARCHAR(20) NOT NULL DEFAULT 'STRING', system_flag TINYINT DEFAULT 1,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500), UNIQUE KEY uk_config_key(config_key)
) ENGINE=InnoDB;

CREATE TABLE sys_dict_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, dict_name VARCHAR(100) NOT NULL, dict_type VARCHAR(100) NOT NULL, status TINYINT DEFAULT 1,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500), UNIQUE KEY uk_dict_type(dict_type)
) ENGINE=InnoDB;

CREATE TABLE sys_dict_data (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, dict_type VARCHAR(100) NOT NULL, dict_label VARCHAR(100) NOT NULL,
  dict_value VARCHAR(100) NOT NULL, sort_no INT DEFAULT 0, status TINYINT DEFAULT 1, color_type VARCHAR(30),
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500), INDEX idx_dict_type(dict_type)
) ENGINE=InnoDB;

CREATE TABLE sys_oper_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT, username VARCHAR(50), dept_id BIGINT,
  operation_type VARCHAR(50) NOT NULL, module VARCHAR(50) NOT NULL, content VARCHAR(1000),
  request_method VARCHAR(10), request_uri VARCHAR(500), request_ip VARCHAR(64), result TINYINT NOT NULL,
  failure_reason VARCHAR(1000), cost_time BIGINT,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500), INDEX idx_oper_time(create_time)
) ENGINE=InnoDB;

CREATE TABLE sys_login_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(50), login_ip VARCHAR(64), browser VARCHAR(500),
  os VARCHAR(500), status TINYINT NOT NULL, message VARCHAR(500), login_time DATETIME NOT NULL,
  create_by BIGINT DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by BIGINT DEFAULT 0, update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, remark VARCHAR(500), INDEX idx_login_time(create_time)
) ENGINE=InnoDB;

ALTER TABLE sys_user ADD CONSTRAINT fk_user_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id);
ALTER TABLE sys_auth_session ADD CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES sys_user(id);
ALTER TABLE sys_user_role ADD CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
  ADD CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role(id);
ALTER TABLE sys_role_menu ADD CONSTRAINT fk_role_menu_role FOREIGN KEY (role_id) REFERENCES sys_role(id),
  ADD CONSTRAINT fk_role_menu_menu FOREIGN KEY (menu_id) REFERENCES sys_menu(id);
ALTER TABLE base_police_station ADD CONSTRAINT fk_station_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id);
ALTER TABLE base_target_jurisdiction ADD CONSTRAINT fk_jurisdiction_station
  FOREIGN KEY (station_dept_id) REFERENCES sys_dept(id);
ALTER TABLE check_period_snapshot ADD CONSTRAINT fk_snapshot_executor
  FOREIGN KEY (executor_dept_id) REFERENCES sys_dept(id);
ALTER TABLE check_task ADD CONSTRAINT fk_task_initiator FOREIGN KEY (initiator_dept_id) REFERENCES sys_dept(id),
  ADD CONSTRAINT fk_task_executor FOREIGN KEY (executor_dept_id) REFERENCES sys_dept(id);
ALTER TABLE check_record ADD CONSTRAINT fk_record_task FOREIGN KEY (task_id) REFERENCES check_task(id);
ALTER TABLE check_attachment ADD CONSTRAINT fk_attachment_task FOREIGN KEY (task_id) REFERENCES check_task(id),
  ADD CONSTRAINT fk_attachment_record FOREIGN KEY (record_id) REFERENCES check_record(id);
ALTER TABLE hidden_danger ADD CONSTRAINT fk_danger_task FOREIGN KEY (task_id) REFERENCES check_task(id);
ALTER TABLE remind_record ADD CONSTRAINT fk_remind_task FOREIGN KEY (task_id) REFERENCES check_task(id),
  ADD CONSTRAINT fk_remind_user FOREIGN KEY (receiver_user_id) REFERENCES sys_user(id),
  ADD CONSTRAINT fk_remind_dept FOREIGN KEY (receiver_dept_id) REFERENCES sys_dept(id);
ALTER TABLE coverage_stat ADD CONSTRAINT fk_coverage_dept FOREIGN KEY (dept_id) REFERENCES sys_dept(id);

SET FOREIGN_KEY_CHECKS = 1;


