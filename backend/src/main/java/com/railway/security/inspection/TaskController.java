package com.railway.security.inspection;

import com.railway.security.inspection.TaskApplicationService.TaskFullDetail;
import com.railway.security.inspection.TaskApplicationService.TaskListSummary;
import com.railway.security.inspection.TaskApplicationService.TaskSearchCriteria;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.shared.web.ApiResponse;
import com.railway.security.shared.web.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/check-tasks")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('BUREAU','STATION')")
public class TaskController {
    private static final MediaType XLSX =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final TaskApplicationService taskApplicationService;
    private final TaskService taskService;
    private final TaskSelectionService taskSelectionService;

    @GetMapping
    public ApiResponse<PageResult<CheckTask>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String checkType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) Integer halfYear,
            @RequestParam(required = false) Integer overdue,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetName,
            @RequestParam(required = false) String completion,
            @RequestParam(required = false) String creationMode) {
        return ApiResponse.ok(
                taskApplicationService.list(
                        criteria(
                                page,
                                size,
                                status,
                                checkType,
                                keyword,
                                year,
                                quarter,
                                halfYear,
                                overdue,
                                targetType,
                                targetName,
                                completion,
                                creationMode)));
    }

    @GetMapping("/summary")
    public ApiResponse<TaskListSummary> summary(
            @RequestParam(required = false) String checkType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) Integer halfYear,
            @RequestParam(required = false) Integer overdue,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetName) {
        return ApiResponse.ok(
                taskApplicationService.summary(
                        criteria(
                                1,
                                100,
                                null,
                                checkType,
                                keyword,
                                year,
                                quarter,
                                halfYear,
                                overdue,
                                targetType,
                                targetName,
                                null,
                                null)));
    }

    @GetMapping("/overdue-reminders")
    public ApiResponse<PageResult<CheckTask>> overdueReminders(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "8") long size) {
        return ApiResponse.ok(taskApplicationService.overdueReminders(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<CheckTask> detail(@PathVariable Long id) {
        return ApiResponse.ok(taskApplicationService.detail(id));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String checkType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) Integer overdue,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetName) {
        byte[] data =
                taskApplicationService.export(
                        criteria(
                                1,
                                100,
                                status,
                                checkType,
                                keyword,
                                year,
                                quarter,
                                null,
                                overdue,
                                targetType,
                                targetName,
                                null,
                                null));
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("检查任务.xlsx", StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(data);
    }

    @GetMapping("/selection-status")
    public ApiResponse<List<TaskSelectionService.SelectionStatus>> selectionStatus(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return ApiResponse.ok(taskSelectionService.statuses(year, quarter));
    }

    @PostMapping("/initialize-period")
    public ApiResponse<List<TaskSelectionService.SelectionStatus>> initializePeriod(
            @Valid @RequestBody PeriodRequest request) {
        return ApiResponse.ok(
                taskSelectionService.initializePeriod(request.year(), request.quarter()));
    }

    @GetMapping("/selection-pool")
    public ApiResponse<TaskSelectionService.SelectionPool> selectionPool(
            @RequestParam String targetType,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return ApiResponse.ok(taskSelectionService.pool(targetType, year, quarter));
    }

    @PostMapping("/selection-save")
    public ApiResponse<TaskSelectionService.SelectionPool> selectionSave(
            @Valid @RequestBody TaskSelectionService.SelectionSaveRequest request) {
        return ApiResponse.ok(taskSelectionService.save(request));
    }

    @PostMapping("/selection-clear")
    public ApiResponse<TaskSelectionService.SelectionPool> selectionClear(
            @Valid @RequestBody TaskSelectionService.SelectionClearRequest request) {
        return ApiResponse.ok(taskSelectionService.clear(request));
    }

    @GetMapping("/{id}/full")
    public ApiResponse<TaskFullDetail> fullDetail(@PathVariable Long id) {
        return ApiResponse.ok(taskApplicationService.fullDetail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody CheckTask update) {
        taskApplicationService.update(id, update);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<Void> submit(@PathVariable Long id) {
        taskService.submit(id);
        return ApiResponse.ok();
    }

    private TaskSearchCriteria criteria(
            long page,
            long size,
            String status,
            String checkType,
            String keyword,
            Integer year,
            Integer quarter,
            Integer halfYear,
            Integer overdue,
            String targetType,
            String targetName,
            String completion,
            String creationMode) {
        return new TaskSearchCriteria(
                page,
                size,
                status,
                checkType,
                keyword,
                year,
                quarter,
                halfYear,
                overdue,
                targetType,
                targetName,
                completion,
                creationMode);
    }

    public record PeriodRequest(@NotNull Integer year, @NotNull @Min(1) @Max(4) Integer quarter) {}
}
