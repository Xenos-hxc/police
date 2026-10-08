package com.railway.security.statistics;

import com.railway.security.inspection.TaskExcelService;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.shared.web.ApiResponse;
import com.railway.security.shared.web.PageResult;
import com.railway.security.statistics.DashboardService.DashboardCharts;
import com.railway.security.statistics.DashboardService.DashboardSummary;
import com.railway.security.system.SystemPeriodService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController {
    private static final MediaType XLSX =
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final DashboardService dashboardService;
    private final StatisticsService statisticsService;
    private final TaskExcelService taskExcelService;
    private final SystemPeriodService systemPeriodService;

    @GetMapping("/api/dashboard/summary")
    @PreAuthorize("hasAnyRole('BUREAU','STATION')")
    public ApiResponse<DashboardSummary> summary() {
        return ApiResponse.ok(dashboardService.summary());
    }

    @GetMapping("/api/dashboard/charts")
    @PreAuthorize("hasAnyRole('BUREAU','STATION')")
    public ApiResponse<DashboardCharts> charts() {
        return ApiResponse.ok(dashboardService.charts());
    }

    @GetMapping("/api/dashboard/todo")
    @PreAuthorize("hasAnyRole('BUREAU','STATION')")
    public ApiResponse<PageResult<CheckTask>> todo(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.ok(dashboardService.todo(page, size));
    }

    @GetMapping("/api/dashboard/warnings")
    @PreAuthorize("hasAnyRole('BUREAU','STATION')")
    public ApiResponse<PageResult<CheckTask>> warnings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.ok(dashboardService.warnings(page, size));
    }

    @GetMapping("/api/statistics/coverage")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU','STATION')")
    public ApiResponse<StatisticsService.Overview> coverage(
            StatisticsService.StatisticsQuery query) {
        return ApiResponse.ok(statisticsService.overview(query));
    }

    @GetMapping({"/api/statistics/submission", "/api/statistics/overdue"})
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU','STATION')")
    public ApiResponse<StatisticsService.Overview> genericStatistics(
            StatisticsService.StatisticsQuery query) {
        return ApiResponse.ok(statisticsService.overview(query));
    }

    @GetMapping("/api/statistics/overview")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU','STATION')")
    public ApiResponse<StatisticsService.Overview> overview(
            StatisticsService.StatisticsQuery query) {
        return ApiResponse.ok(statisticsService.overview(query));
    }

    @GetMapping("/api/statistics/charts")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU','STATION')")
    public ApiResponse<Map<String, Object>> statisticsCharts(
            StatisticsService.StatisticsQuery query) {
        return ApiResponse.ok(statisticsService.charts(query));
    }

    @GetMapping("/api/statistics/ranking")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU')")
    public ApiResponse<PageResult<StatisticsService.RankingItem>> ranking(
            StatisticsService.StatisticsQuery query) {
        return ApiResponse.ok(statisticsService.ranking(query));
    }

    @GetMapping("/api/statistics/export")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU','STATION')")
    public ResponseEntity<byte[]> export(StatisticsService.StatisticsQuery query) {
        var effectivePeriod = systemPeriodService.effectivePeriod();
        int year = query.year() == null ? effectivePeriod.year() : query.year();
        int quarter = query.quarter() == null ? effectivePeriod.quarter() : query.quarter();
        byte[] data = taskExcelService.export(statisticsService.scopedTasks(query, year, quarter));
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("统计分析.xlsx", StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(data);
    }
}
