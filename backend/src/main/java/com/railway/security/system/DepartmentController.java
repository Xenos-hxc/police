package com.railway.security.system;

import com.railway.security.shared.web.ApiResponse;
import com.railway.security.system.dto.SystemDtos.DepartmentNode;
import com.railway.security.system.dto.SystemDtos.DepartmentResponse;
import com.railway.security.system.dto.SystemDtos.DepartmentSaveRequest;
import com.railway.security.system.dto.SystemDtos.StatusUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/depts")
@RequiredArgsConstructor
public class DepartmentController {
    private final DepartmentAdministrationService departmentService;

    @GetMapping("/tree")
    public ApiResponse<List<DepartmentNode>> tree() {
        return ApiResponse.ok(departmentService.tree());
    }

    @GetMapping("/{id}")
    public ApiResponse<DepartmentResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(departmentService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU')")
    public ApiResponse<DepartmentResponse> create(
            @Valid @RequestBody DepartmentSaveRequest request) {
        return ApiResponse.ok(departmentService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU')")
    public ApiResponse<Void> update(
            @PathVariable Long id, @Valid @RequestBody DepartmentSaveRequest request) {
        departmentService.update(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU')")
    public ApiResponse<Void> changeStatus(
            @PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        departmentService.changeStatus(id, request.status());
        return ApiResponse.ok();
    }
}
