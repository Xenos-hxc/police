package com.railway.security.file;

import com.railway.security.persistence.entity.CheckAttachment;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@RequiredArgsConstructor
public class FileScanEvents {
    private static final int MAX_SUBSCRIBERS = 100;
    private final FileApplicationService files;
    private final Map<SseEmitter, Subscription> subscribers = new ConcurrentHashMap<>();

    // SSE 连接具有身份快照、订阅上限与超时；重连轮询当前状态不等于事件重放，长期连接还需考虑权限变更。
    public SseEmitter subscribe(Long taskId) {
        List<CheckAttachment> initial = files.list(taskId);
        if (subscribers.size() >= MAX_SUBSCRIBERS) {
            throw new com.railway.security.shared.web.BusinessException(429, "状态订阅过多，请稍后重试");
        }
        var emitter = new SseEmitter(60_000L);
        var context = SecurityContextHolder.getContext();
        var subscription = new Subscription(taskId, context, new AtomicReference<>(""));
        subscribers.put(emitter, subscription);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(error -> subscribers.remove(emitter));
        send(emitter, initial, subscription);
        return emitter;
    }

    @Scheduled(fixedDelay = 3000)
    public void refresh() {
        for (var entry : subscribers.entrySet()) {
            var previous = SecurityContextHolder.getContext();
            try {
                SecurityContextHolder.setContext(entry.getValue().securityContext());
                send(entry.getKey(), files.list(entry.getValue().taskId()), entry.getValue());
            } catch (Exception exception) {
                subscribers.remove(entry.getKey());
                entry.getKey().complete();
            } finally {
                SecurityContextHolder.setContext(previous);
            }
        }
    }

    private void send(
            SseEmitter emitter, List<CheckAttachment> attachments, Subscription subscription) {
        var snapshot =
                attachments.stream()
                        .map(
                                file ->
                                        new ScanState(
                                                file.getId(),
                                                file.getScanStatus(),
                                                file.getStorageStatus()))
                        .toList();
        String signature = snapshot.toString();
        if (signature.equals(subscription.last().get())) return;
        try {
            emitter.send(SseEmitter.event().name("scans").data(snapshot));
            subscription.last().set(signature);
        } catch (IOException exception) {
            subscribers.remove(emitter);
            emitter.complete();
        }
    }

    private record Subscription(
            Long taskId, SecurityContext securityContext, AtomicReference<String> last) {}

    public record ScanState(Long id, String scanStatus, String storageStatus) {}
}
