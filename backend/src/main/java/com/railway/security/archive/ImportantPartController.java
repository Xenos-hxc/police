package com.railway.security.archive;

import com.railway.security.archive.ArchiveDtos.SearchCriteria;
import com.railway.security.persistence.entity.ImportantPart;
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
@RequestMapping("/api/important-parts")
@RequiredArgsConstructor
public class ImportantPartController {
    private final ArchiveCatalogueQueryService queryService;
    private final ArchiveService archiveService;

    @GetMapping
    public ApiResponse<PageResult<ImportantPart>> list(
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
                queryService.parts(
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
    public ApiResponse<ImportantPart> detail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.part(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU')")
    public ApiResponse<ImportantPart> create(@Valid @RequestBody ImportantPart entity) {
        return ApiResponse.ok(archiveService.createPart(entity));
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU')")
    public ApiResponse<Void> update(
            @PathVariable Long id, @Valid @RequestBody ImportantPart entity) {
        archiveService.updatePart(id, entity);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        archiveService.archive("important-parts", id);
        return ApiResponse.ok();
    }
}
