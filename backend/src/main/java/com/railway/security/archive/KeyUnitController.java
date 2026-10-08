package com.railway.security.archive;

import com.railway.security.archive.ArchiveDtos.SearchCriteria;
import com.railway.security.persistence.entity.KeyUnit;
import com.railway.security.shared.web.ApiResponse;
import com.railway.security.shared.web.PageResult;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
@RequestMapping("/api/key-units")
@RequiredArgsConstructor
public class KeyUnitController {
    private final ArchiveCatalogueQueryService queryService;
    private final ArchiveService archiveService;

    @GetMapping
    public ApiResponse<PageResult<KeyUnit>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String typeOrArea,
            @RequestParam(required = false) String leader,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long stationDeptId,
            @RequestParam(required = false) Integer archived,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return ApiResponse.ok(
                queryService.units(
                        new SearchCriteria(
                                page,
                                size,
                                name,
                                typeOrArea,
                                leader,
                                phone,
                                status,
                                stationDeptId,
                                archived,
                                startDate,
                                endDate)));
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<KeyUnit> detail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.unit(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU')")
    public ApiResponse<KeyUnit> create(@Valid @RequestBody KeyUnit entity) {
        return ApiResponse.ok(archiveService.createUnit(entity));
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody KeyUnit entity) {
        archiveService.updateUnit(id, entity);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        archiveService.archive("key-units", id);
        return ApiResponse.ok();
    }
}
