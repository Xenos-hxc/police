package com.railway.security.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.archive.ArchiveDtos.ExportFile;
import com.railway.security.archive.ArchiveDtos.ImportResponse;
import com.railway.security.persistence.entity.ImportantPart;
import com.railway.security.persistence.entity.KeyUnit;
import com.railway.security.persistence.entity.PoliceStation;
import com.railway.security.persistence.mapper.ImportantPartMapper;
import com.railway.security.persistence.mapper.KeyUnitMapper;
import com.railway.security.persistence.mapper.PoliceStationMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.shared.security.SecurityUtils;
import com.railway.security.shared.web.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ArchiveTransferService {
    private final PoliceStationMapper stationMapper;
    private final KeyUnitMapper unitMapper;
    private final ImportantPartMapper partMapper;
    private final DataScopeService dataScopeService;
    private final ArchiveService archiveService;
    private final ArchiveExcelService excelService;

    @Transactional(readOnly = true)
    public ExportFile export(String type) {
        byte[] content =
                switch (type) {
                    case "police-stations" ->
                            excelService.exportStations(
                                    stationMapper.selectList(
                                            new LambdaQueryWrapper<PoliceStation>()
                                                    .in(
                                                            PoliceStation::getId,
                                                            safeIds(
                                                                    dataScopeService
                                                                            .permittedTargetIds(
                                                                                    "STATION")))));
                    case "key-units" -> {
                        var rows =
                                unitMapper.selectList(
                                        new LambdaQueryWrapper<KeyUnit>()
                                                .in(
                                                        KeyUnit::getId,
                                                        safeIds(
                                                                dataScopeService.permittedTargetIds(
                                                                        "KEY_UNIT"))));
                        rows.forEach(archiveService::enrich);
                        yield excelService.exportUnits(rows);
                    }
                    case "important-parts" -> {
                        var rows =
                                partMapper.selectList(
                                        new LambdaQueryWrapper<ImportantPart>()
                                                .in(
                                                        ImportantPart::getId,
                                                        safeIds(
                                                                dataScopeService.permittedTargetIds(
                                                                        "IMPORTANT_PART"))));
                        rows.forEach(archiveService::enrich);
                        yield excelService.exportParts(rows);
                    }
                    default -> throw new BusinessException("不支持的档案类型");
                };
        return new ExportFile(type + ".xlsx", content);
    }

    @Transactional
    public ImportResponse importFile(String type, MultipartFile file) {
        assertArchivePermission(type);
        return new ImportResponse(excelService.importFile(type, file));
    }

    public void assertArchivePermission(String type) {
        var roles = SecurityUtils.currentUser().getRoles();
        boolean knownType =
                List.of("police-stations", "key-units", "important-parts").contains(type);
        if (!knownType || !(roles.contains("ADMIN") || roles.contains("BUREAU"))) {
            throw new BusinessException(403, "当前角色不能维护该类档案");
        }
    }

    private List<Long> safeIds(List<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of(-1L) : ids;
    }
}
