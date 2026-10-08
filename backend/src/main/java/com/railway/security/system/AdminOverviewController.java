package com.railway.security.system;

import com.railway.security.shared.web.ApiResponse;
import com.railway.security.system.dto.SystemDtos.AdminOverviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOverviewController {
    private final AdminOverviewService overviewService;

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewResponse> overview() {
        return ApiResponse.ok(overviewService.overview());
    }
}
