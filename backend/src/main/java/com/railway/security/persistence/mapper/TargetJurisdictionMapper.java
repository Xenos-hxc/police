package com.railway.security.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.railway.security.persistence.entity.TargetJurisdiction;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface TargetJurisdictionMapper extends BaseMapper<TargetJurisdiction> {
    @Update(
            """
            UPDATE base_target_jurisdiction
            SET deleted = 1, update_by = #{operatorId}, update_time = NOW()
            WHERE target_type = #{targetType} AND target_id = #{targetId} AND deleted = 0
            """)
    int deactivateTarget(
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("operatorId") Long operatorId);

    @Insert(
            """
            INSERT INTO base_target_jurisdiction
                (station_dept_id, target_type, target_id, create_by, update_by, deleted)
            VALUES
                (#{stationDeptId}, #{targetType}, #{targetId}, #{operatorId}, #{operatorId}, 0)
            ON DUPLICATE KEY UPDATE
                deleted = 0, update_by = #{operatorId}, update_time = NOW()
            """)
    int upsert(
            @Param("stationDeptId") Long stationDeptId,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("operatorId") Long operatorId);
}
