ALTER TABLE check_attachment
  ADD COLUMN scan_attempts INT NOT NULL DEFAULT 0 AFTER scan_status,
  ADD COLUMN scan_next_attempt_at DATETIME NULL AFTER scan_attempts,
  ADD COLUMN scan_started_at DATETIME NULL AFTER scan_next_attempt_at,
  ADD COLUMN scan_error VARCHAR(255) NULL AFTER scan_started_at,
  ADD INDEX idx_attachment_scan_queue (deleted, scan_status, scan_next_attempt_at);
