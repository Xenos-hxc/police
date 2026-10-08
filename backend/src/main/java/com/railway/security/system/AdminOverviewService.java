package com.railway.security.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.ImportantPart;
import com.railway.security.persistence.entity.KeyUnit;
import com.railway.security.persistence.entity.PoliceStation;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.entity.TargetJurisdiction;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.ImportantPartMapper;
import com.railway.security.persistence.mapper.KeyUnitMapper;
import com.railway.security.persistence.mapper.PoliceStationMapper;
import com.railway.security.persistence.mapper.TargetJurisdictionMapper;
import com.railway.security.persistence.mapper.UserMapper;
import com.railway.security.persistence.mapper.UserRoleCodeProjection;
import com.railway.security.system.dto.SystemDtos.AdminOverviewResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOverviewService {
    private final DeptMapper deptMapper;
    private final UserMapper userMapper;
    private final PoliceStationMapper stationMapper;
    private final KeyUnitMapper unitMapper;
    private final ImportantPartMapper partMapper;
    private final TargetJurisdictionMapper jurisdictionMapper;

    @Transactional(readOnly = true)
    public AdminOverviewResponse overview() {
        List<SysDept> departments =
                deptMapper.selectList(
                        new LambdaQueryWrapper<SysDept>()
                                .in(SysDept::getDeptType, List.of("BUREAU", "STATION"))
                                .eq(SysDept::getStatus, 1));
        List<SysUser> users =
                userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1));
        Map<Long, SysDept> departmentsById =
                departments.stream()
                        .collect(Collectors.toMap(SysDept::getId, department -> department));
        Map<Long, Set<String>> rolesByUser =
                userMapper
                        .selectRoleCodesByUserIds(users.stream().map(SysUser::getId).toList())
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        UserRoleCodeProjection::userId,
                                        Collectors.mapping(
                                                UserRoleCodeProjection::roleCode,
                                                Collectors.toSet())));

        Map<String, Long> departmentCounts =
                departments.stream()
                        .collect(
                                Collectors.groupingBy(
                                        SysDept::getDeptType,
                                        LinkedHashMap::new,
                                        Collectors.counting()));
        Set<Long> coveredDepartmentIds =
                users.stream()
                        .filter(
                                user -> {
                                    SysDept department = departmentsById.get(user.getDeptId());
                                    if (department == null) {
                                        return false;
                                    }
                                    String expectedRole =
                                            "BUREAU".equals(department.getDeptType())
                                                    ? "BUREAU"
                                                    : "STATION";
                                    return rolesByUser
                                            .getOrDefault(user.getId(), Set.of())
                                            .contains(expectedRole);
                                })
                        .map(SysUser::getDeptId)
                        .collect(Collectors.toSet());
        Map<String, Long> accountCovered =
                departments.stream()
                        .filter(department -> coveredDepartmentIds.contains(department.getId()))
                        .collect(
                                Collectors.groupingBy(
                                        SysDept::getDeptType,
                                        LinkedHashMap::new,
                                        Collectors.counting()));

        List<TargetJurisdiction> relations =
                jurisdictionMapper.selectList(new LambdaQueryWrapper<TargetJurisdiction>());
        Map<String, Long> relationCounts =
                relations.stream()
                        .collect(
                                Collectors.groupingBy(
                                        TargetJurisdiction::getTargetType,
                                        LinkedHashMap::new,
                                        Collectors.counting()));
        Map<String, Set<Long>> assignedTargetIds =
                relations.stream()
                        .collect(
                                Collectors.groupingBy(
                                        TargetJurisdiction::getTargetType,
                                        LinkedHashMap::new,
                                        Collectors.mapping(
                                                TargetJurisdiction::getTargetId,
                                                Collectors.toSet())));
        Map<String, Long> assignedTargetCounts =
                assignedTargetIds.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        Map.Entry::getKey,
                                        entry -> (long) entry.getValue().size()));
        Map<String, Long> sharedTargetCounts =
                relations.stream()
                        .collect(
                                Collectors.groupingBy(
                                        relation ->
                                                relation.getTargetType()
                                                        + ":"
                                                        + relation.getTargetId()))
                        .entrySet()
                        .stream()
                        .filter(entry -> entry.getValue().size() > 1)
                        .collect(
                                Collectors.groupingBy(
                                        entry ->
                                                entry.getKey()
                                                        .substring(0, entry.getKey().indexOf(':')),
                                        LinkedHashMap::new,
                                        Collectors.counting()));

        long stationCount = stationMapper.selectCount(new LambdaQueryWrapper<PoliceStation>());
        long unitCount = unitMapper.selectCount(new LambdaQueryWrapper<KeyUnit>());
        long partCount = partMapper.selectCount(new LambdaQueryWrapper<ImportantPart>());
        return new AdminOverviewResponse(
                departmentCounts,
                Map.of("STATION", stationCount, "KEY_UNIT", unitCount, "IMPORTANT_PART", partCount),
                accountCovered,
                users.size(),
                departments.stream()
                        .filter(department -> !coveredDepartmentIds.contains(department.getId()))
                        .count(),
                relationCounts,
                sharedTargetCounts,
                Map.of(
                        "KEY_UNIT",
                                Math.max(
                                        0,
                                        unitCount
                                                - assignedTargetCounts.getOrDefault(
                                                        "KEY_UNIT", 0L)),
                        "IMPORTANT_PART",
                                Math.max(
                                        0,
                                        partCount
                                                - assignedTargetCounts.getOrDefault(
                                                        "IMPORTANT_PART", 0L))));
    }
}
