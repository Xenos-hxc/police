package com.railway.security.file;

import com.railway.security.shared.cache.TemporaryValueStore;
import com.railway.security.shared.web.BusinessException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileDownloadTicketService {
    private static final Duration TICKET_TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final TemporaryValueStore store;

    public String issue(Long attachmentId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        store.put("download:ticket:" + token, attachmentId.toString(), TICKET_TTL);
        return token;
    }

    // 短时票据绑定附件并原子消费；防重放不等于网络恰好送达，下载失败后的重试应重新授权获取票据。
    public void validate(String token, Long attachmentId) {
        if (token == null || !token.matches("[0-9a-f]{64}")) throw invalidTicket();
        String storedId = store.getAndDelete("download:ticket:" + token);
        if (!attachmentId.toString().equals(storedId)) throw invalidTicket();
    }

    private BusinessException invalidTicket() {
        return new BusinessException(403, "下载凭证无效或已过期，请重新点击下载");
    }
}
