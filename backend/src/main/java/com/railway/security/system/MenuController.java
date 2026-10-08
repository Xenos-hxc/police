package com.railway.security.system;

import com.railway.security.shared.web.ApiResponse;
import com.railway.security.system.dto.SystemDtos.MenuResponse;
import com.railway.security.system.dto.SystemDtos.MenuSaveRequest;
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
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {
    private final AuthorizationAdministrationService authorizationService;

    @GetMapping
    public ApiResponse<List<MenuResponse>> menus() {
        return ApiResponse.ok(authorizationService.menus());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MenuResponse> create(@Valid @RequestBody MenuSaveRequest request) {
        return ApiResponse.ok(authorizationService.createMenu(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> update(
            @PathVariable Long id, @Valid @RequestBody MenuSaveRequest request) {
        authorizationService.updateMenu(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        authorizationService.deleteMenu(id);
        return ApiResponse.ok();
    }
}
