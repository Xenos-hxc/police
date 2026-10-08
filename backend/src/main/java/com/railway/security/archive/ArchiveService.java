package com.railway.security.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.CheckTask;
import com.railway.security.persistence.entity.ImportantPart;
import com.railway.security.persistence.entity.KeyUnit;
import com.railway.security.persistence.entity.PoliceStation;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.entity.TargetJurisdiction;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.ImportantPartMapper;
import com.railway.security.persistence.mapper.KeyUnitMapper;
import com.railway.security.persistence.mapper.PoliceStationMapper;
import com.railway.security.persistence.mapper.TargetJurisdictionMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.shared.security.SecurityUtils;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.statistics.StatisticsCacheService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArchiveService {
    private final PoliceStationMapper stationMapper;
    private final KeyUnitMapper unitMapper;
    private final ImportantPartMapper partMapper;
    private final TargetJurisdictionMapper jurisdictionMapper;
    private final DeptMapper deptMapper;
    private final CheckTaskMapper taskMapper;
    private final DataScopeService dataScopeService;
    private final StatisticsCacheService statisticsCacheService;

    @Transactional
    public PoliceStation createStation(PoliceStation station) {
        ensureText(station.getStationName(), "派出所名称不能为空");
        if (station.getStationCode() == null || station.getStationCode().isBlank()) {
            station.setStationCode(generateInternalCode("PS"));
        }
        ensureUniqueStationCode(station.getStationCode(), null);
        SysDept dept =
                station.getDeptId() == null
                        ? createStationDept(station)
                        : deptMapper.selectById(station.getDeptId());
        if (dept == null || !"STATION".equals(dept.getDeptType())) {
            throw new BusinessException("请选择有效的派出所部门");
        }
        station.setDeptId(dept.getId());
        station.setStatus(station.getStatus() == null ? 1 : station.getStatus());
        station.setArchived(0);
        stationMapper.insert(station);
        statisticsCacheService.clear();
        return station;
    }

    @Transactional
    public void updateStation(Long id, PoliceStation update) {
        var station = requiredStation(id);
        dataScopeService.checkDept(station.getDeptId());
        ensureText(update.getStationName(), "派出所名称不能为空");
        if (update.getStationCode() != null && !update.getStationCode().isBlank()) {
            ensureUniqueStationCode(update.getStationCode(), id);
            station.setStationCode(update.getStationCode());
        }
        station.setStationName(update.getStationName());
        station.setLeader(update.getLeader());
        station.setPhone(update.getPhone());
        station.setAddress(update.getAddress());
        station.setJurisdiction(update.getJurisdiction());
        station.setStatus(update.getStatus() == null ? station.getStatus() : update.getStatus());
        station.setRemark(update.getRemark());
        stationMapper.updateById(station);
        syncStationDept(station);
        statisticsCacheService.clear();
    }

    @Transactional
    public KeyUnit createUnit(KeyUnit unit) {
        ensureText(unit.getUnitName(), "重点单位名称不能为空");
        if (unit.getUnitCode() == null || unit.getUnitCode().isBlank()) {
            unit.setUnitCode(generateInternalCode("KU"));
        }
        ensureUniqueUnitCode(unit.getUnitCode(), null);
        List<Long> stationDeptIds = assignmentsForCreate(unit.getStationDeptIds());
        unit.setStatus(unit.getStatus() == null ? 1 : unit.getStatus());
        unit.setArchived(0);
        unitMapper.insert(unit);
        replaceJurisdictions("KEY_UNIT", unit.getId(), stationDeptIds);
        statisticsCacheService.clear();
        return unit;
    }

    @Transactional
    public void updateUnit(Long id, KeyUnit update) {
        var unit = requiredUnit(id);
        dataScopeService.checkTarget("KEY_UNIT", id);
        ensureText(update.getUnitName(), "重点单位名称不能为空");
        if (update.getUnitCode() == null || update.getUnitCode().isBlank()) {
            update.setUnitCode(unit.getUnitCode());
        } else {
            ensureUniqueUnitCode(update.getUnitCode(), id);
        }
        copyUnit(update, unit);
        unitMapper.updateById(unit);
        if (dataScopeService.hasGlobalBusinessAccess()) {
            replaceJurisdictions(
                    "KEY_UNIT", id, validateStationDeptIds(update.getStationDeptIds()));
        }
        statisticsCacheService.clear();
    }

    @Transactional
    public ImportantPart createPart(ImportantPart part) {
        ensureText(part.getPartName(), "重要部位名称不能为空");
        if (part.getPartCode() == null || part.getPartCode().isBlank()) {
            part.setPartCode(generateInternalCode("IP"));
        }
        ensureUniquePartCode(part.getPartCode(), null);
        List<Long> stationDeptIds = assignmentsForCreate(part.getStationDeptIds());
        part.setStatus(part.getStatus() == null ? 1 : part.getStatus());
        part.setArchived(0);
        partMapper.insert(part);
        replaceJurisdictions("IMPORTANT_PART", part.getId(), stationDeptIds);
        statisticsCacheService.clear();
        return part;
    }

    @Transactional
    public void updatePart(Long id, ImportantPart update) {
        var part = requiredPart(id);
        dataScopeService.checkTarget("IMPORTANT_PART", id);
        ensureText(update.getPartName(), "重要部位名称不能为空");
        if (update.getPartCode() == null || update.getPartCode().isBlank()) {
            update.setPartCode(part.getPartCode());
        } else {
            ensureUniquePartCode(update.getPartCode(), id);
        }
        copyPart(update, part);
        partMapper.updateById(part);
        if (dataScopeService.hasGlobalBusinessAccess()) {
            replaceJurisdictions(
                    "IMPORTANT_PART", id, validateStationDeptIds(update.getStationDeptIds()));
        }
        statisticsCacheService.clear();
    }

    @Transactional
    public void changeStatus(String type, Long id, int status) {
        if (status != 0 && status != 1) throw new BusinessException("档案状态无效");
        switch (type) {
            case "police-stations" -> {
                var entity = requiredStation(id);
                dataScopeService.checkDept(entity.getDeptId());
                entity.setStatus(status);
                stationMapper.updateById(entity);
                syncStationDept(entity);
            }
            case "key-units" -> {
                dataScopeService.checkTarget("KEY_UNIT", id);
                var entity = requiredUnit(id);
                entity.setStatus(status);
                unitMapper.updateById(entity);
            }
            case "important-parts" -> {
                dataScopeService.checkTarget("IMPORTANT_PART", id);
                var entity = requiredPart(id);
                entity.setStatus(status);
                partMapper.updateById(entity);
            }
            default -> throw new BusinessException("不支持的档案类型");
        }
        statisticsCacheService.clear();
    }

    @Transactional
    public void archive(String type, Long id) {
        String targetType = targetType(type);
        if (hasActiveTasks(id, targetType)) {
            throw new BusinessException("存在未完成检查任务，不能归档");
        }
        switch (type) {
            case "police-stations" -> {
                var entity = requiredStation(id);
                dataScopeService.checkDept(entity.getDeptId());
                entity.setArchived(1);
                entity.setStatus(0);
                stationMapper.updateById(entity);
                syncStationDept(entity);
            }
            case "key-units" -> {
                dataScopeService.checkTarget("KEY_UNIT", id);
                var entity = requiredUnit(id);
                entity.setArchived(1);
                entity.setStatus(0);
                unitMapper.updateById(entity);
            }
            case "important-parts" -> {
                dataScopeService.checkTarget("IMPORTANT_PART", id);
                var entity = requiredPart(id);
                entity.setArchived(1);
                entity.setStatus(0);
                partMapper.updateById(entity);
            }
            default -> throw new BusinessException("不支持的档案类型");
        }
        statisticsCacheService.clear();
    }

    public void enrich(KeyUnit unit) {
        if (unit == null) return;
        var rows = jurisdictions("KEY_UNIT", unit.getId());
        unit.setStationDeptIds(rows.stream().map(TargetJurisdiction::getStationDeptId).toList());
        unit.setStationNames(stationNames(unit.getStationDeptIds()));
    }

    public void enrich(ImportantPart part) {
        if (part == null) return;
        var rows = jurisdictions("IMPORTANT_PART", part.getId());
        part.setStationDeptIds(rows.stream().map(TargetJurisdiction::getStationDeptId).toList());
        part.setStationNames(stationNames(part.getStationDeptIds()));
    }

    private List<Long> assignmentsForCreate(List<Long> requested) {
        if (dataScopeService.hasGlobalBusinessAccess()) {
            return validateStationDeptIds(requested);
        }
        List<Long> own = List.of(SecurityUtils.currentUser().getDeptId());
        dataScopeService.checkStationAssignments(own);
        return own;
    }

    private List<Long> validateStationDeptIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        var distinct = new LinkedHashSet<>(ids);
        for (Long id : distinct) {
            var dept = deptMapper.selectById(id);
            if (dept == null || !"STATION".equals(dept.getDeptType())) {
                throw new BusinessException("存在无效的管辖派出所");
            }
        }
        return List.copyOf(distinct);
    }

    private void replaceJurisdictions(String targetType, Long targetId, List<Long> stationDeptIds) {
        Long operatorId = SecurityUtils.currentUserIdOrZero();
        jurisdictionMapper.deactivateTarget(targetType, targetId, operatorId);
        for (Long stationDeptId : stationDeptIds) {
            jurisdictionMapper.upsert(stationDeptId, targetType, targetId, operatorId);
        }
    }

    private List<TargetJurisdiction> jurisdictions(String targetType, Long targetId) {
        return jurisdictionMapper.selectList(
                new LambdaQueryWrapper<TargetJurisdiction>()
                        .eq(TargetJurisdiction::getTargetType, targetType)
                        .eq(TargetJurisdiction::getTargetId, targetId)
                        .orderByAsc(TargetJurisdiction::getStationDeptId));
    }

    private List<String> stationNames(List<Long> stationDeptIds) {
        if (stationDeptIds == null || stationDeptIds.isEmpty()) return List.of();
        return deptMapper.selectBatchIds(stationDeptIds).stream()
                .sorted(java.util.Comparator.comparing(SysDept::getSortNo))
                .map(SysDept::getDeptName)
                .toList();
    }

    private SysDept createStationDept(PoliceStation station) {
        var parent =
                deptMapper.selectOne(
                        new LambdaQueryWrapper<SysDept>().eq(SysDept::getDeptType, "BUREAU"));
        if (parent == null) throw new BusinessException("公安处部门不存在");
        var dept = new SysDept();
        dept.setParentId(parent.getId());
        dept.setAncestors(parent.getAncestors() + "," + parent.getId());
        dept.setDeptName(station.getStationName());
        dept.setDeptType("STATION");
        dept.setLeader(station.getLeader());
        dept.setPhone(station.getPhone());
        dept.setAddress(station.getAddress());
        dept.setJurisdiction(station.getJurisdiction());
        dept.setSortNo(
                (int)
                        (deptMapper.selectCount(
                                        new LambdaQueryWrapper<SysDept>()
                                                .eq(SysDept::getDeptType, "STATION"))
                                + 1));
        dept.setStatus(station.getStatus() == null ? 1 : station.getStatus());
        dept.setArchived(0);
        deptMapper.insert(dept);
        return dept;
    }

    private void syncStationDept(PoliceStation station) {
        var dept = deptMapper.selectById(station.getDeptId());
        if (dept == null) return;
        dept.setDeptName(station.getStationName());
        dept.setLeader(station.getLeader());
        dept.setPhone(station.getPhone());
        dept.setAddress(station.getAddress());
        dept.setJurisdiction(station.getJurisdiction());
        dept.setStatus(station.getStatus());
        dept.setArchived(station.getArchived());
        dept.setRemark(station.getRemark());
        deptMapper.updateById(dept);
    }

    private void copyUnit(KeyUnit source, KeyUnit target) {
        target.setUnitCode(source.getUnitCode());
        target.setUnitName(source.getUnitName());
        target.setUnitType(source.getUnitType());
        target.setEstablishedDate(source.getEstablishedDate());
        target.setLeader(source.getLeader());
        target.setContactPerson(source.getContactPerson());
        target.setPhone(source.getPhone());
        target.setAddress(source.getAddress());
        target.setBureauName(source.getBureauName());
        target.setJurisdictionText(source.getJurisdictionText());
        target.setStatus(source.getStatus() == null ? target.getStatus() : source.getStatus());
        target.setRemark(source.getRemark());
    }

    private void copyPart(ImportantPart source, ImportantPart target) {
        target.setPartCode(source.getPartCode());
        target.setPartName(source.getPartName());
        target.setPartType(source.getPartType());
        target.setEstablishedDate(source.getEstablishedDate());
        target.setRemovedDate(source.getRemovedDate());
        target.setGuardStatus(source.getGuardStatus());
        target.setLengthDescription(source.getLengthDescription());
        target.setRailwayLine(source.getRailwayLine());
        target.setKilometerMark(source.getKilometerMark());
        target.setLocation(source.getLocation());
        target.setResponsibleUnit(source.getResponsibleUnit());
        target.setWorkshop(source.getWorkshop());
        target.setBureauName(source.getBureauName());
        target.setJurisdictionText(source.getJurisdictionText());
        target.setStatus(source.getStatus() == null ? target.getStatus() : source.getStatus());
        target.setRemark(source.getRemark());
    }

    private boolean hasActiveTasks(Long targetId, String targetType) {
        return taskMapper.selectCount(
                        new LambdaQueryWrapper<CheckTask>()
                                .eq(CheckTask::getTargetId, targetId)
                                .eq(CheckTask::getTargetType, targetType)
                                .in(CheckTask::getStatus, List.of("PENDING", "OVERDUE"))
                                .eq(CheckTask::getCountCoverage, 1))
                > 0;
    }

    private String targetType(String type) {
        return switch (type) {
            case "police-stations" -> "STATION";
            case "key-units" -> "KEY_UNIT";
            case "important-parts" -> "IMPORTANT_PART";
            default -> throw new BusinessException("不支持的档案类型");
        };
    }

    private String generateInternalCode(String prefix) {
        return prefix
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private void ensureUniqueStationCode(String code, Long excludeId) {
        if (stationMapper.selectCount(
                        new LambdaQueryWrapper<PoliceStation>()
                                .eq(PoliceStation::getStationCode, code)
                                .ne(excludeId != null, PoliceStation::getId, excludeId))
                > 0) {
            throw new BusinessException("派出所编号已存在");
        }
    }

    private void ensureUniqueUnitCode(String code, Long excludeId) {
        if (unitMapper.selectCount(
                        new LambdaQueryWrapper<KeyUnit>()
                                .eq(KeyUnit::getUnitCode, code)
                                .ne(excludeId != null, KeyUnit::getId, excludeId))
                > 0) {
            throw new BusinessException("重点单位编号已存在");
        }
    }

    private void ensureUniquePartCode(String code, Long excludeId) {
        if (partMapper.selectCount(
                        new LambdaQueryWrapper<ImportantPart>()
                                .eq(ImportantPart::getPartCode, code)
                                .ne(excludeId != null, ImportantPart::getId, excludeId))
                > 0) {
            throw new BusinessException("重要部位编号已存在");
        }
    }

    private PoliceStation requiredStation(Long id) {
        var entity = stationMapper.selectById(id);
        if (entity == null) throw new BusinessException("派出所档案不存在");
        return entity;
    }

    private KeyUnit requiredUnit(Long id) {
        var entity = unitMapper.selectById(id);
        if (entity == null) throw new BusinessException("重点单位档案不存在");
        return entity;
    }

    private ImportantPart requiredPart(Long id) {
        var entity = partMapper.selectById(id);
        if (entity == null) throw new BusinessException("重要部位档案不存在");
        return entity;
    }

    private void ensureText(String value, String message) {
        if (value == null || value.isBlank()) throw new BusinessException(message);
    }
}
