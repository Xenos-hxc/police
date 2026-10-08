-- Enforce invariants that previously existed only in service code.
ALTER TABLE check_task
  MODIFY quarter TINYINT NOT NULL,
  ADD CONSTRAINT ck_task_quarter CHECK (quarter BETWEEN 1 AND 4),
  ADD CONSTRAINT ck_task_target_type CHECK (target_type IN ('STATION','KEY_UNIT','IMPORTANT_PART')),
  ADD CONSTRAINT ck_task_flags CHECK (overdue IN (0,1) AND overdue_submitted IN (0,1)
    AND count_coverage IN (0,1)),
  ADD INDEX idx_task_execution_period
    (executor_dept_id, task_year, quarter, target_type, count_coverage, status);

ALTER TABLE check_period_snapshot
  ADD CONSTRAINT ck_snapshot_quarter CHECK (quarter BETWEEN 1 AND 4),
  ADD CONSTRAINT ck_snapshot_counts CHECK (total_count >= 0 AND required_count >= 0
    AND required_count <= total_count),
  ADD CONSTRAINT ck_snapshot_percent CHECK (coverage_percent BETWEEN 0 AND 100);

ALTER TABLE check_record
  ADD CONSTRAINT fk_record_submitter FOREIGN KEY (submitted_by) REFERENCES sys_user(id);

ALTER TABLE hidden_danger
  ADD CONSTRAINT fk_danger_executor FOREIGN KEY (executor_dept_id) REFERENCES sys_dept(id),
  ADD CONSTRAINT fk_danger_submitter FOREIGN KEY (rectification_submitted_by) REFERENCES sys_user(id);

ALTER TABLE check_attachment
  ADD INDEX idx_attachment_task_type (task_id, attachment_type, deleted);

ALTER TABLE remind_record
  ADD INDEX idx_remind_receiver (receiver_dept_id, read_flag, remind_time);
