package com.railway.security.system;

import com.railway.security.shared.web.ApiResponse;
import com.railway.security.shared.web.PageResult;
import com.railway.security.system.dto.SystemDtos.PasswordResetRequest;
import com.railway.security.system.dto.SystemDtos.StatusUpdateRequest;
import com.railway.security.system.dto.SystemDtos.UserFormResponse;
import com.railway.security.system.dto.SystemDtos.UserResponse;
import com.railway.security.system.dto.SystemDtos.UserSaveRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserAdministrationController {
    private final UserAdministrationService userService;

    @GetMapping
    public ApiResponse<PageResult<UserResponse>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String deptType) {
        return ApiResponse.ok(userService.page(page, size, keyword, status, deptId, deptType));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserFormResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.get(id));
    }

    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody UserSaveRequest request) {
        return ApiResponse.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        userService.update(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(
            @PathVariable Long id, @RequestBody(required = false) PasswordResetRequest request) {
        userService.resetPassword(id, request == null ? null : request.password());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> changeStatus(
            @PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        userService.changeStatus(id, request.status());
        return ApiResponse.ok();
    }
}
