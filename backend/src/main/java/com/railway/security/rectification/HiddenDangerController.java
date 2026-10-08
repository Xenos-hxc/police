package com.railway.security.rectification;

import com.railway.security.shared.web.ApiResponse;
import com.railway.security.shared.web.PageResult;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hidden-dangers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('BUREAU','STATION')")
public class HiddenDangerController {
    private final HiddenDangerService hiddenDangerService;

    @GetMapping
    public ApiResponse<PageResult<HiddenDangerService.DangerView>> list(
            HiddenDangerService.DangerQuery query) {
        return ApiResponse.ok(hiddenDangerService.list(query));
    }

    @GetMapping("/target-types")
    public ApiResponse<List<String>> targetTypes() {
        return ApiResponse.ok(hiddenDangerService.accessibleTargetTypes());
    }

    @GetMapping("/unfinished-counts")
    public ApiResponse<Map<String, Long>> unfinishedCounts() {
        return ApiResponse.ok(hiddenDangerService.unfinishedCounts());
    }

    @GetMapping("/default-deadline-days")
    public ApiResponse<Integer> defaultDeadlineDays() {
        return ApiResponse.ok(hiddenDangerService.defaultDeadlineDays());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(hiddenDangerService.detail(id));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<Void> submit(
            @PathVariable Long id, @RequestBody HiddenDangerService.RectificationRequest request) {
        hiddenDangerService.submitRectification(id, request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> save(
            @PathVariable Long id, @RequestBody HiddenDangerService.RectificationRequest request) {
        hiddenDangerService.saveRectificationDraft(id, request);
        return ApiResponse.ok();
    }
}
