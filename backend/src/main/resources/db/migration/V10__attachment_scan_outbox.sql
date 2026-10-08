CREATE TABLE attachment_scan_outbox (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  attachment_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME NULL,
  claimed_at DATETIME NULL,
  published_at DATETIME NULL,
  last_error VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_scan_outbox_attachment (attachment_id),
  KEY idx_scan_outbox_ready (status, next_attempt_at)
);
