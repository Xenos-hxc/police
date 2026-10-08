package com.railway.security.rectification;

import com.railway.security.persistence.entity.RemindRecord;
import com.railway.security.shared.web.ApiResponse;
import com.railway.security.shared.web.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('BUREAU','STATION')")
public class ReminderController {
    private final ReminderService reminderService;

    @GetMapping
    public ApiResponse<PageResult<RemindRecord>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Integer readFlag) {
        return ApiResponse.ok(reminderService.list(page, size, readFlag));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.ok(reminderService.unreadCount());
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id) {
        reminderService.markRead(id);
        return ApiResponse.ok();
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> readAll() {
        reminderService.markAllRead();
        return ApiResponse.ok();
    }
}
