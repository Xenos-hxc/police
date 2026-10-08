ALTER TABLE ai_assistance_run
  ADD COLUMN trace_id CHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000' AFTER user_id,
  ADD INDEX idx_ai_run_trace (trace_id);
