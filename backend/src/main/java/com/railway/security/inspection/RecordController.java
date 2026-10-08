package com.railway.security.inspection;

import com.railway.security.persistence.entity.CheckRecord;
import com.railway.security.shared.web.ApiResponse;
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
@RequestMapping("/api/check-records")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('BUREAU','STATION')")
public class RecordController {
    private final CheckRecordService recordService;
    private final InspectionSubmissionService inspectionSubmissionService;

    @GetMapping("/{taskId}")
    public ApiResponse<CheckRecord> byTask(@PathVariable Long taskId) {
        return ApiResponse.ok(recordService.byTask(taskId));
    }

    @PostMapping
    public ApiResponse<CheckRecord> create(@RequestBody CheckRecord record) {
        return ApiResponse.ok(recordService.create(record));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody CheckRecord update) {
        recordService.update(id, update);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<Void> submit(@PathVariable Long id) {
        inspectionSubmissionService.submit(id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/print")
    public ApiResponse<CheckRecord> print(@PathVariable Long id) {
        return ApiResponse.ok(recordService.printable(id));
    }
}
