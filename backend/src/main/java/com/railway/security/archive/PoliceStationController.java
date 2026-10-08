package com.railway.security.archive;

import com.railway.security.archive.ArchiveDtos.SearchCriteria;
import com.railway.security.persistence.entity.ImportantPart;
import com.railway.security.persistence.entity.KeyUnit;
import com.railway.security.persistence.entity.PoliceStation;
import com.railway.security.shared.web.ApiResponse;
import com.railway.security.shared.web.PageResult;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/police-stations")
@RequiredArgsConstructor
public class PoliceStationController {
    private final ArchiveCatalogueQueryService queryService;
    private final ArchiveService archiveService;

    @GetMapping
    public ApiResponse<PageResult<PoliceStation>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String typeOrArea,
            @RequestParam(required = false) String leader,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer archived,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return ApiResponse.ok(
                queryService.stations(
                        criteria(
                                page,
                                size,
                                name,
                                typeOrArea,
                                leader,
                                phone,
                                status,
                                null,
                                archived,
                                startDate,
                                endDate)));
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<PoliceStation> detail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.station(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU')")
    public ApiResponse<PoliceStation> create(@Valid @RequestBody PoliceStation entity) {
        return ApiResponse.ok(archiveService.createStation(entity));
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU')")
    public ApiResponse<Void> update(
            @PathVariable Long id, @Valid @RequestBody PoliceStation entity) {
        archiveService.updateStation(id, entity);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('ADMIN','BUREAU')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        archiveService.archive("police-stations", id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id:\\d+}/units")
    public ApiResponse<List<KeyUnit>> units(@PathVariable Long id) {
        return ApiResponse.ok(queryService.stationUnits(id));
    }

    @GetMapping("/{id:\\d+}/parts")
    public ApiResponse<List<ImportantPart>> parts(@PathVariable Long id) {
        return ApiResponse.ok(queryService.stationParts(id));
    }

    private SearchCriteria criteria(
            long page,
            long size,
            String name,
            String typeOrArea,
            String leader,
            String phone,
            Integer status,
            Long stationDeptId,
            Integer archived,
            LocalDate startDate,
            LocalDate endDate) {
        return new SearchCriteria(
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
                endDate);
    }
}
