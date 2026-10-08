package com.railway.security.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.railway.security.persistence.entity.AuthSession;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface AuthSessionMapper extends BaseMapper<AuthSession> {
    @Update(
            """
            UPDATE sys_auth_session
               SET refresh_token_hash=#{newHash}, expires_at=#{expiresAt}, last_used_at=#{usedAt},
                   client_ip=#{clientIp}, user_agent=#{userAgent}, update_time=#{usedAt}
             WHERE id=#{id} AND refresh_token_hash=#{oldHash} AND revoked_at IS NULL
               AND expires_at > #{usedAt} AND deleted=0
            """)
    int rotate(
            @Param("id") Long id,
            @Param("oldHash") String oldHash,
            @Param("newHash") String newHash,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("usedAt") LocalDateTime usedAt,
            @Param("clientIp") String clientIp,
            @Param("userAgent") String userAgent);
}
