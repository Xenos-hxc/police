package com.railway.security.inspection;

import com.railway.security.shared.web.ApiResponse;
import com.railway.security.shared.web.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/station-inspections")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BUREAU')")
public class StationInspectionController {
    private final StationInspectionService stationInspectionService;

    @GetMapping("/progress")
    public ApiResponse<PageResult<StationInspectionService.StationProgress>> progress(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) String stationName,
            @RequestParam(required = false) String completionOrder,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(
                stationInspectionService.progress(
                        year, quarter, stationName, completionOrder, page, size));
    }

    @GetMapping("/{stationDeptId}")
    public ApiResponse<StationInspectionService.StationDetail> detail(
            @PathVariable Long stationDeptId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(
                stationInspectionService.detail(
                        stationDeptId, year, quarter, targetType, targetName, status, page, size));
    }
}
