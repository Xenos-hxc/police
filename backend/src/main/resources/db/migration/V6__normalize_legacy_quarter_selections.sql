-- Older data selected CEIL(total * quarter_percent) independently for every quarter.
-- The current cumulative rule can make a later quarter one item smaller (for example,
-- 185 * 25% is distributed as 47/46/46/46). Preserve every submitted task and only
-- remove surplus unsubmitted selections from the coverage set.
UPDATE check_task task
JOIN (
    SELECT ranked.id
    FROM (
        SELECT pending.id,
               ROW_NUMBER() OVER (
                   PARTITION BY pending.executor_dept_id, pending.task_year,
                                pending.quarter, pending.target_type
                   ORDER BY pending.id
               ) AS pending_rank,
               GREATEST(snapshot.required_count - COALESCE(completed.completed_count, 0), 0)
                   AS pending_allowance
        FROM check_task pending
        JOIN check_period_snapshot snapshot
          ON snapshot.executor_dept_id = pending.executor_dept_id
         AND snapshot.task_year = pending.task_year
         AND snapshot.quarter = pending.quarter
         AND snapshot.target_type = pending.target_type
         AND snapshot.deleted = 0
        LEFT JOIN (
            SELECT executor_dept_id, task_year, quarter, target_type, COUNT(*) AS completed_count
            FROM check_task
            WHERE deleted = 0
              AND count_coverage = 1
              AND status IN ('APPROVED', 'OVERDUE_SUBMITTED')
            GROUP BY executor_dept_id, task_year, quarter, target_type
        ) completed
          ON completed.executor_dept_id = pending.executor_dept_id
         AND completed.task_year = pending.task_year
         AND completed.quarter = pending.quarter
         AND completed.target_type = pending.target_type
        WHERE pending.deleted = 0
          AND pending.count_coverage = 1
          AND pending.status NOT IN ('APPROVED', 'OVERDUE_SUBMITTED')
    ) ranked
    WHERE ranked.pending_rank > ranked.pending_allowance
) surplus ON surplus.id = task.id
SET task.count_coverage = 0,
    task.creation_mode = 'SYSTEM',
    task.update_time = CURRENT_TIMESTAMP;
