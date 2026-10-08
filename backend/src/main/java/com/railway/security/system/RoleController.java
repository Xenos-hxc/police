package com.railway.security.system;

import com.railway.security.shared.web.ApiResponse;
import com.railway.security.system.dto.SystemDtos.RoleDetailResponse;
import com.railway.security.system.dto.SystemDtos.RoleResponse;
import com.railway.security.system.dto.SystemDtos.RoleSaveRequest;
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
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RoleController {
    private final AuthorizationAdministrationService authorizationService;

    @GetMapping
    public ApiResponse<List<RoleResponse>> roles() {
        return ApiResponse.ok(authorizationService.roles());
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleDetailResponse> role(@PathVariable Long id) {
        return ApiResponse.ok(authorizationService.role(id));
    }

    @PostMapping
    public ApiResponse<RoleResponse> create(@Valid @RequestBody RoleSaveRequest request) {
        return ApiResponse.ok(authorizationService.createRole(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long id, @Valid @RequestBody RoleSaveRequest request) {
        authorizationService.updateRole(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        authorizationService.deleteRole(id);
        return ApiResponse.ok();
    }
}
