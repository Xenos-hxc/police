package com.railway.security.archive;

import com.railway.security.archive.ArchiveDtos.HistoryCriteria;
import com.railway.security.archive.ArchiveDtos.HistoryDetailResponse;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.shared.web.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BUREAU','STATION')")
public class ArchiveHistoryController {
    private final ArchiveHistoryService historyService;

    @GetMapping("/api/{type:police-stations|key-units|important-parts}/{id:\\d+}/history")
    public ApiResponse<List<CheckTask>> history(
            @PathVariable String type,
            @PathVariable Long id,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return ApiResponse.ok(
                historyService.history(
                        type, id, new HistoryCriteria(year, quarter, startDate, endDate)));
    }

    @GetMapping("/api/{type:police-stations|key-units|important-parts}/history-detail/{id:\\d+}")
    public ApiResponse<HistoryDetailResponse> detail(
            @PathVariable String type,
            @PathVariable Long id,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return ApiResponse.ok(
                historyService.detail(
                        type, id, new HistoryCriteria(year, quarter, startDate, endDate)));
    }
}
