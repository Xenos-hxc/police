package com.railway.security.system;

import com.railway.security.shared.web.ApiResponse;
import com.railway.security.system.dto.SystemDtos.ConfigResponse;
import com.railway.security.system.dto.SystemDtos.ConfigUpdateRequest;
import com.railway.security.system.dto.SystemDtos.DictionaryDataResponse;
import com.railway.security.system.dto.SystemDtos.DictionaryDataSaveRequest;
import com.railway.security.system.dto.SystemDtos.DictionaryTypeResponse;
import com.railway.security.system.dto.SystemDtos.DictionaryTypeSaveRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReferenceDataController {
    private final ReferenceDataAdministrationService referenceDataService;
    private final SystemPeriodService systemPeriodService;

    @GetMapping("/configs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<ConfigResponse>> configs() {
        return ApiResponse.ok(referenceDataService.configs());
    }

    @PutMapping("/configs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateConfigs(@Valid @RequestBody List<ConfigUpdateRequest> requests) {
        referenceDataService.updateConfigs(requests);
        return ApiResponse.ok();
    }

    @GetMapping("/system-period")
    public ApiResponse<SystemPeriodService.PeriodInfo> systemPeriod() {
        return ApiResponse.ok(systemPeriodService.info());
    }

    @GetMapping("/dict-types")
    public ApiResponse<List<DictionaryTypeResponse>> dictionaryTypes() {
        return ApiResponse.ok(referenceDataService.dictionaryTypes());
    }

    @PostMapping("/dict-types")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DictionaryTypeResponse> createDictionaryType(
            @Valid @RequestBody DictionaryTypeSaveRequest request) {
        return ApiResponse.ok(referenceDataService.createDictionaryType(request));
    }

    @PutMapping("/dict-types/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateDictionaryType(
            @PathVariable Long id, @Valid @RequestBody DictionaryTypeSaveRequest request) {
        referenceDataService.updateDictionaryType(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/dict-types/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteDictionaryType(@PathVariable Long id) {
        referenceDataService.deleteDictionaryType(id);
        return ApiResponse.ok();
    }

    @GetMapping("/dicts")
    public ApiResponse<List<DictionaryDataResponse>> dictionaries(
            @RequestParam(required = false) String type) {
        return ApiResponse.ok(referenceDataService.dictionaries(type));
    }

    @PostMapping("/dicts")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DictionaryDataResponse> createDictionary(
            @Valid @RequestBody DictionaryDataSaveRequest request) {
        return ApiResponse.ok(referenceDataService.createDictionary(request));
    }

    @PutMapping("/dicts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateDictionary(
            @PathVariable Long id, @Valid @RequestBody DictionaryDataSaveRequest request) {
        referenceDataService.updateDictionary(id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/dicts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteDictionary(@PathVariable Long id) {
        referenceDataService.deleteDictionary(id);
        return ApiResponse.ok();
    }
}
