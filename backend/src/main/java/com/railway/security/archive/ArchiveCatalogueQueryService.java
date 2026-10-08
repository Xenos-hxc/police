package com.railway.security.archive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.railway.security.archive.ArchiveDtos.SearchCriteria;
import com.railway.security.persistence.entity.ImportantPart;
import com.railway.security.persistence.entity.KeyUnit;
import com.railway.security.persistence.entity.PoliceStation;
import com.railway.security.persistence.entity.TargetJurisdiction;
import com.railway.security.persistence.mapper.ImportantPartMapper;
import com.railway.security.persistence.mapper.KeyUnitMapper;
import com.railway.security.persistence.mapper.PoliceStationMapper;
import com.railway.security.persistence.mapper.TargetJurisdictionMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.shared.web.PageResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ArchiveCatalogueQueryService {
    private final PoliceStationMapper stationMapper;
    private final KeyUnitMapper unitMapper;
    private final ImportantPartMapper partMapper;
    private final TargetJurisdictionMapper jurisdictionMapper;
    private final DataScopeService dataScopeService;
    private final ArchiveService archiveService;

    @Transactional(readOnly = true)
    public PageResult<PoliceStation> stations(SearchCriteria criteria) {
        var result =
                stationMapper.selectPage(
                        page(criteria),
                        new LambdaQueryWrapper<PoliceStation>()
                                .in(
                                        PoliceStation::getId,
                                        safeIds(dataScopeService.permittedTargetIds("STATION")))
                                .like(
                                        StringUtils.hasText(criteria.name()),
                                        PoliceStation::getStationName,
                                        criteria.name())
                                .like(
                                        StringUtils.hasText(criteria.typeOrArea()),
                                        PoliceStation::getJurisdiction,
                                        criteria.typeOrArea())
                                .like(
                                        StringUtils.hasText(criteria.leader()),
                                        PoliceStation::getLeader,
                                        criteria.leader())
                                .like(
                                        StringUtils.hasText(criteria.phone()),
                                        PoliceStation::getPhone,
                                        criteria.phone())
                                .eq(
                                        criteria.status() != null,
                                        PoliceStation::getStatus,
                                        criteria.status())
                                .eq(
                                        criteria.archived() != null,
                                        PoliceStation::getArchived,
                                        criteria.archived())
                                .ge(
                                        criteria.startDate() != null,
                                        PoliceStation::getCreateTime,
                                        start(criteria))
                                .lt(
                                        criteria.endDate() != null,
                                        PoliceStation::getCreateTime,
                                        endExclusive(criteria))
                                .orderByAsc(PoliceStation::getId));
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Transactional(readOnly = true)
    public PoliceStation station(Long id) {
        var entity = requiredStation(id);
        dataScopeService.checkTarget("STATION", id);
        return entity;
    }

    @Transactional(readOnly = true)
    public List<KeyUnit> stationUnits(Long stationId) {
        var station = requiredStation(stationId);
        dataScopeService.checkDept(station.getDeptId());
        var rows =
                unitMapper.selectList(
                        new LambdaQueryWrapper<KeyUnit>()
                                .in(
                                        KeyUnit::getId,
                                        safeIds(
                                                jurisdictionTargetIds(
                                                        station.getDeptId(), "KEY_UNIT")))
                                .orderByAsc(KeyUnit::getUnitName));
        rows.forEach(archiveService::enrich);
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ImportantPart> stationParts(Long stationId) {
        var station = requiredStation(stationId);
        dataScopeService.checkDept(station.getDeptId());
        var rows =
                partMapper.selectList(
                        new LambdaQueryWrapper<ImportantPart>()
                                .in(
                                        ImportantPart::getId,
                                        safeIds(
                                                jurisdictionTargetIds(
                                                        station.getDeptId(), "IMPORTANT_PART")))
                                .orderByAsc(ImportantPart::getPartName));
        rows.forEach(archiveService::enrich);
        return rows;
    }

    @Transactional(readOnly = true)
    public PageResult<KeyUnit> units(SearchCriteria criteria) {
        var result =
                unitMapper.selectPage(
                        page(criteria),
                        new LambdaQueryWrapper<KeyUnit>()
                                .in(
                                        KeyUnit::getId,
                                        safeIds(
                                                filteredTargetIds(
                                                        "KEY_UNIT", criteria.stationDeptId())))
                                .like(
                                        StringUtils.hasText(criteria.name()),
                                        KeyUnit::getUnitName,
                                        criteria.name())
                                .and(
                                        StringUtils.hasText(criteria.typeOrArea()),
                                        wrapper ->
                                                wrapper.like(
                                                                KeyUnit::getUnitType,
                                                                criteria.typeOrArea())
                                                        .or()
                                                        .like(
                                                                KeyUnit::getJurisdictionText,
                                                                criteria.typeOrArea())
                                                        .or()
                                                        .like(
                                                                KeyUnit::getAddress,
                                                                criteria.typeOrArea()))
                                .and(
                                        StringUtils.hasText(criteria.leader()),
                                        wrapper ->
                                                wrapper.like(KeyUnit::getLeader, criteria.leader())
                                                        .or()
                                                        .like(
                                                                KeyUnit::getContactPerson,
                                                                criteria.leader()))
                                .like(
                                        StringUtils.hasText(criteria.phone()),
                                        KeyUnit::getPhone,
                                        criteria.phone())
                                .eq(
                                        criteria.status() != null,
                                        KeyUnit::getStatus,
                                        criteria.status())
                                .eq(
                                        criteria.archived() != null,
                                        KeyUnit::getArchived,
                                        criteria.archived())
                                .ge(
                                        criteria.startDate() != null,
                                        KeyUnit::getCreateTime,
                                        start(criteria))
                                .lt(
                                        criteria.endDate() != null,
                                        KeyUnit::getCreateTime,
                                        endExclusive(criteria))
                                .orderByAsc(KeyUnit::getId));
        result.getRecords().forEach(archiveService::enrich);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Transactional(readOnly = true)
    public KeyUnit unit(Long id) {
        var entity = unitMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("重点单位档案不存在");
        }
        dataScopeService.checkTarget("KEY_UNIT", id);
        archiveService.enrich(entity);
        return entity;
    }

    @Transactional(readOnly = true)
    public PageResult<ImportantPart> parts(SearchCriteria criteria) {
        var result =
                partMapper.selectPage(
                        page(criteria),
                        new LambdaQueryWrapper<ImportantPart>()
                                .in(
                                        ImportantPart::getId,
                                        safeIds(
                                                filteredTargetIds(
                                                        "IMPORTANT_PART",
                                                        criteria.stationDeptId())))
                                .like(
                                        StringUtils.hasText(criteria.name()),
                                        ImportantPart::getPartName,
                                        criteria.name())
                                .and(
                                        StringUtils.hasText(criteria.typeOrArea()),
                                        wrapper ->
                                                wrapper.like(
                                                                ImportantPart::getPartType,
                                                                criteria.typeOrArea())
                                                        .or()
                                                        .like(
                                                                ImportantPart::getJurisdictionText,
                                                                criteria.typeOrArea())
                                                        .or()
                                                        .like(
                                                                ImportantPart::getRailwayLine,
                                                                criteria.typeOrArea())
                                                        .or()
                                                        .like(
                                                                ImportantPart::getLocation,
                                                                criteria.typeOrArea()))
                                .and(
                                        StringUtils.hasText(criteria.leader()),
                                        wrapper ->
                                                wrapper.like(
                                                                ImportantPart::getResponsibleUnit,
                                                                criteria.leader())
                                                        .or()
                                                        .like(
                                                                ImportantPart::getWorkshop,
                                                                criteria.leader()))
                                .eq(
                                        criteria.status() != null,
                                        ImportantPart::getStatus,
                                        criteria.status())
                                .eq(
                                        criteria.archived() != null,
                                        ImportantPart::getArchived,
                                        criteria.archived())
                                .ge(
                                        criteria.startDate() != null,
                                        ImportantPart::getCreateTime,
                                        start(criteria))
                                .lt(
                                        criteria.endDate() != null,
                                        ImportantPart::getCreateTime,
                                        endExclusive(criteria))
                                .orderByAsc(ImportantPart::getId));
        result.getRecords().forEach(archiveService::enrich);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    @Transactional(readOnly = true)
    public ImportantPart part(Long id) {
        var entity = partMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("重要部位档案不存在");
        }
        dataScopeService.checkTarget("IMPORTANT_PART", id);
        archiveService.enrich(entity);
        return entity;
    }

    private <T> Page<T> page(SearchCriteria criteria) {
        return new Page<>(
                Math.max(1, criteria.page()), Math.max(1, Math.min(criteria.size(), 100)));
    }

    private java.time.LocalDateTime start(SearchCriteria criteria) {
        return criteria.startDate() == null ? null : criteria.startDate().atStartOfDay();
    }

    private java.time.LocalDateTime endExclusive(SearchCriteria criteria) {
        return criteria.endDate() == null ? null : criteria.endDate().plusDays(1).atStartOfDay();
    }

    private List<Long> filteredTargetIds(String targetType, Long stationDeptId) {
        var permitted = new ArrayList<>(dataScopeService.permittedTargetIds(targetType));
        if (stationDeptId == null) {
            return permitted;
        }
        dataScopeService.checkDept(stationDeptId);
        Set<Long> assigned = Set.copyOf(jurisdictionTargetIds(stationDeptId, targetType));
        return permitted.stream().filter(assigned::contains).toList();
    }

    private List<Long> jurisdictionTargetIds(Long stationDeptId, String targetType) {
        return jurisdictionMapper
                .selectList(
                        new LambdaQueryWrapper<TargetJurisdiction>()
                                .eq(TargetJurisdiction::getStationDeptId, stationDeptId)
                                .eq(TargetJurisdiction::getTargetType, targetType))
                .stream()
                .map(TargetJurisdiction::getTargetId)
                .distinct()
                .toList();
    }

    private PoliceStation requiredStation(Long id) {
        var station = stationMapper.selectById(id);
        if (station == null) {
            throw new BusinessException("派出所档案不存在");
        }
        return station;
    }

    private List<Long> safeIds(List<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of(-1L) : ids;
    }
}
