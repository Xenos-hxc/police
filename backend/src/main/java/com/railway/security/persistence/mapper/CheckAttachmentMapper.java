package com.railway.security.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.railway.security.persistence.entity.CheckAttachment;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface CheckAttachmentMapper extends BaseMapper<CheckAttachment> {
    @Select(
            """
            SELECT * FROM check_attachment
             WHERE deleted=1 AND purged_at IS NULL AND update_time < #{cutoff}
             ORDER BY id LIMIT #{limit}
            """)
    List<CheckAttachment> selectDeletedForPurge(
            @Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

    @Update(
            """
            UPDATE check_attachment SET storage_status='PURGED', purged_at=NOW(), update_time=NOW()
             WHERE id=#{id} AND deleted=1 AND purged_at IS NULL
            """)
    int markPurged(@Param("id") Long id);

    @Select(
            """
            SELECT id FROM check_attachment
             WHERE deleted=0 AND scan_status='PENDING' AND storage_status='QUARANTINED'
               AND (scan_next_attempt_at IS NULL OR scan_next_attempt_at <= NOW())
             ORDER BY id LIMIT #{limit}
            """)
    List<Long> pendingScanIds(@Param("limit") int limit);

    @Update(
            """
            UPDATE check_attachment
               SET scan_status='SCANNING', scan_attempts=scan_attempts+1,
                   scan_started_at=NOW(), scan_error=NULL, update_time=NOW()
             WHERE id=#{id} AND deleted=0 AND scan_status='PENDING'
            """)
    // 原子认领依赖 UPDATE 的状态条件；当前语句没有完整重复候选查询的到期与存储条件，改进设计可追加条件及 claimVersion。
    int claimScan(@Param("id") Long id);

    @Update(
            """
            UPDATE check_attachment
               SET scan_status='PENDING', scan_started_at=NULL, update_time=NOW()
             WHERE deleted=0 AND scan_status='SCANNING'
               AND scan_started_at < DATE_SUB(NOW(), INTERVAL 2 HOUR)
            """)
    int recoverAbandonedScans();

    @Update(
            """
            UPDATE check_attachment
               SET scan_status=#{scanStatus}, storage_status='ACTIVE', sha256=#{sha256},
                   scan_started_at=NULL, scan_next_attempt_at=NULL, scan_error=NULL,
                   update_time=NOW()
             WHERE id=#{id} AND deleted=0 AND scan_status='SCANNING'
            """)
    int finishScan(
            @Param("id") Long id,
            @Param("scanStatus") String scanStatus,
            @Param("sha256") String sha256);

    @Update(
            """
            UPDATE check_attachment
               SET scan_status=#{scanStatus}, scan_started_at=NULL, scan_error=#{error},
                   scan_next_attempt_at=CASE WHEN #{retrySeconds} IS NULL THEN NULL
                     ELSE DATE_ADD(NOW(), INTERVAL #{retrySeconds} SECOND) END,
                   update_time=NOW()
             WHERE id=#{id} AND deleted=0 AND scan_status='SCANNING'
            """)
    int failScan(
            @Param("id") Long id,
            @Param("scanStatus") String scanStatus,
            @Param("retrySeconds") Integer retrySeconds,
            @Param("error") String error);
}
