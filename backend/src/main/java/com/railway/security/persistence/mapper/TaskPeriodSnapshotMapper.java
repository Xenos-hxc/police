package com.railway.security.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.railway.security.persistence.entity.TaskPeriodSnapshot;
import org.apache.ibatis.annotations.Insert;

public interface TaskPeriodSnapshotMapper extends BaseMapper<TaskPeriodSnapshot> {
    @Insert(
            """
            INSERT IGNORE INTO check_period_snapshot
              (executor_dept_id, executor_dept_type, target_type, task_year, quarter,
               total_count, required_count, coverage_percent, snapshot_status, initialized_at,
               create_by, create_time, update_by, update_time, deleted)
            VALUES
              (#{executorDeptId}, #{executorDeptType}, #{targetType}, #{taskYear}, #{quarter},
               #{totalCount}, #{requiredCount}, #{coveragePercent}, #{snapshotStatus}, #{initializedAt},
               #{createBy}, NOW(), #{createBy}, NOW(), 0)
            """)
    int insertIgnore(TaskPeriodSnapshot snapshot);
}
