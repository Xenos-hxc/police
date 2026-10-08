ALTER TABLE check_task
  ADD COLUMN status_sort TINYINT GENERATED ALWAYS AS (
    CASE status
      WHEN 'PENDING' THEN 0
      WHEN 'OVERDUE' THEN 1
      WHEN 'OVERDUE_SUBMITTED' THEN 2
      WHEN 'APPROVED' THEN 3
      ELSE 4
    END
  ) STORED,
  ADD INDEX idx_task_executor_order
    (executor_dept_id, deleted, status_sort, create_time DESC, id DESC);
