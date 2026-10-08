CREATE TABLE ai_policy_document (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  dept_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  revision VARCHAR(80) NOT NULL,
  source_ref VARCHAR(200) NOT NULL,
  content MEDIUMTEXT NOT NULL,
  content_sha256 CHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'INDEXING',
  created_by BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ai_policy_scope (dept_id, status, deleted)
);

CREATE TABLE ai_assistance_run (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  attachment_id BIGINT NULL,
  dept_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  kind VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  material_sha256 CHAR(64) NULL,
  result_json MEDIUMTEXT NULL,
  error_code VARCHAR(80) NULL,
  latency_ms BIGINT NULL,
  review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  reviewed_by BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ai_run_task (task_id, created_at),
  KEY idx_ai_run_scope (dept_id, status, created_at)
);
