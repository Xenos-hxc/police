package com.railway.security.persistence.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ScanOutboxMapper {
    @Select("SELECT COUNT(*) FROM attachment_scan_outbox WHERE status IN ('PENDING','SENDING')")
    int pendingCount();

    @Select(
            "SELECT COUNT(*) FROM attachment_scan_outbox WHERE status IN ('DEAD_PENDING','DEAD_SENDING','DEAD')")
    int deadCount();

    @Insert("INSERT INTO attachment_scan_outbox (attachment_id) VALUES (#{attachmentId})")
    int enqueue(@Param("attachmentId") Long attachmentId);

    @Select(
            """
            SELECT attachment_id FROM attachment_scan_outbox
             WHERE status='PENDING' AND (next_attempt_at IS NULL OR next_attempt_at<=NOW())
             ORDER BY id LIMIT #{limit}
            """)
    List<Long> pending(@Param("limit") int limit);

    @Select(
            """
            SELECT attachment_id FROM attachment_scan_outbox
             WHERE status='DEAD_PENDING'
               AND (next_attempt_at IS NULL OR next_attempt_at<=NOW())
             ORDER BY id LIMIT #{limit}
            """)
    List<Long> deadPending(@Param("limit") int limit);

    @Update(
            """
            UPDATE attachment_scan_outbox SET status='DEAD_SENDING', claimed_at=NOW()
             WHERE attachment_id=#{id} AND status='DEAD_PENDING'
            """)
    int claimDead(@Param("id") Long id);

    @Update(
            """
            UPDATE attachment_scan_outbox SET status='DEAD_PENDING', claimed_at=NULL,
                   next_attempt_at=DATE_ADD(NOW(), INTERVAL 30 SECOND)
             WHERE attachment_id=#{id} AND status='DEAD_SENDING'
            """)
    int deadPublishFailed(@Param("id") Long id);

    @Update(
            """
            UPDATE attachment_scan_outbox SET status='DEAD_PENDING', claimed_at=NULL,
                   next_attempt_at=NULL
             WHERE status='DEAD_SENDING' AND claimed_at < DATE_SUB(NOW(), INTERVAL 2 MINUTE)
            """)
    int recoverDeadPublishing();

    @Update(
            """
            UPDATE attachment_scan_outbox SET status='SENDING', claimed_at=NOW(),
                   attempts=attempts+1 WHERE attachment_id=#{id} AND status='PENDING'
            """)
    int claim(@Param("id") Long id);

    @Update(
            """
            UPDATE attachment_scan_outbox SET status='SENT', published_at=NOW(),
                   claimed_at=NULL, last_error=NULL
             WHERE attachment_id=#{id} AND status='SENDING'
            """)
    int sent(@Param("id") Long id);

    @Update(
            """
            UPDATE attachment_scan_outbox SET status='PENDING', claimed_at=NULL,
                   next_attempt_at=DATE_ADD(NOW(), INTERVAL 30 SECOND),
                   last_error='消息服务暂不可用'
             WHERE attachment_id=#{id} AND status='SENDING'
            """)
    int publishFailed(@Param("id") Long id);

    @Update(
            """
            UPDATE attachment_scan_outbox SET status='PENDING', claimed_at=NULL,
                   next_attempt_at=NULL, last_error='发布进程中断'
             WHERE status='SENDING' AND claimed_at < DATE_SUB(NOW(), INTERVAL 2 MINUTE)
            """)
    int recoverPublishing();

    @Update(
            """
            UPDATE attachment_scan_outbox o LEFT JOIN check_attachment a ON a.id=o.attachment_id
               SET o.status=CASE WHEN a.scan_status='FAILED' THEN 'DEAD_PENDING' ELSE 'DONE' END,
                   o.next_attempt_at=NULL, o.claimed_at=NULL
             WHERE o.status IN ('PENDING','SENDING','SENT')
               AND (a.id IS NULL OR a.deleted=1
                    OR a.scan_status IN ('CLEAN','SKIPPED','REJECTED','FAILED'))
            """)
    int reconcileFinished();

    @Update(
            """
            UPDATE attachment_scan_outbox o JOIN check_attachment a ON a.id=o.attachment_id
               SET o.status='PENDING', o.next_attempt_at=NULL,
                   o.claimed_at=NULL, o.last_error=NULL
             WHERE o.status='SENT' AND a.deleted=0 AND a.scan_status='PENDING'
               AND a.storage_status='QUARANTINED'
               AND o.published_at < DATE_SUB(NOW(), INTERVAL 15 SECOND)
               AND (a.scan_next_attempt_at IS NULL OR a.scan_next_attempt_at<=NOW())
            """)
    int requeueDueScans();

    @Update(
            """
            UPDATE attachment_scan_outbox SET status='DONE', next_attempt_at=NULL
             WHERE attachment_id=#{id} AND status IN ('PENDING','SENT','SENDING')
            """)
    int done(@Param("id") Long id);

    @Update(
            """
            UPDATE attachment_scan_outbox SET status='DEAD_PENDING', next_attempt_at=NULL,
                   last_error='安全检测重试耗尽'
             WHERE attachment_id=#{id} AND status IN ('PENDING','SENT','SENDING')
            """)
    int dead(@Param("id") Long id);

    @Update(
            """
            UPDATE attachment_scan_outbox SET status='DEAD', published_at=NOW(),
                   next_attempt_at=NULL, claimed_at=NULL
             WHERE attachment_id=#{id} AND status='DEAD_SENDING'
            """)
    int deadPublished(@Param("id") Long id);
}
