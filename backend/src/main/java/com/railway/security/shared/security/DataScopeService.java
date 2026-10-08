package com.railway.security.shared.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.ImportantPart;
import com.railway.security.persistence.entity.KeyUnit;
import com.railway.security.persistence.entity.PoliceStation;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.entity.TargetJurisdiction;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.ImportantPartMapper;
import com.railway.security.persistence.mapper.KeyUnitMapper;
import com.railway.security.persistence.mapper.PoliceStationMapper;
import com.railway.security.persistence.mapper.TargetJurisdictionMapper;
import com.railway.security.shared.web.BusinessException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataScopeService {
    private final DeptMapper deptMapper;
    private final PoliceStationMapper stationMapper;
    private final KeyUnitMapper unitMapper;
    private final ImportantPartMapper partMapper;
    private final TargetJurisdictionMapper jurisdictionMapper;

    public boolean hasGlobalBusinessAccess() {
        var user = SecurityUtils.currentUser();
        return user.getRoles().contains("ADMIN")
                || user.getRoles().contains("BUREAU")
                || "ALL".equals(user.getDataScope());
    }

    // 部门范围由当前身份在服务端计算，不信任请求传入的部门集合；具体资源入口还可施加更严格的业务限制。
    public List<Long> permittedDeptIds() {
        var user = SecurityUtils.currentUser();
        if (hasGlobalBusinessAccess()) {
            return deptMapper.selectList(new LambdaQueryWrapper<SysDept>()).stream()
                    .map(SysDept::getId)
                    .toList();
        }
        if ("DEPT_AND_CHILD".equals(user.getDataScope())) {
            String marker = String.valueOf(user.getDeptId());
            return deptMapper
                    .selectList(
                            new LambdaQueryWrapper<SysDept>()
                                    .and(
                                            w ->
                                                    w.eq(SysDept::getId, user.getDeptId())
                                                            .or()
                                                            .apply(
                                                                    "FIND_IN_SET({0}, ancestors)",
                                                                    marker)))
                    .stream()
                    .map(SysDept::getId)
                    .toList();
        }
        return List.of(user.getDeptId());
    }

    public List<Long> permittedTargetIds(String targetType) {
        String type = normalizeTargetType(targetType);
        if (hasGlobalBusinessAccess()) {
            return switch (type) {
                case "STATION" ->
                        stationMapper.selectList(new LambdaQueryWrapper<PoliceStation>()).stream()
                                .map(PoliceStation::getId)
                                .toList();
                case "KEY_UNIT" ->
                        unitMapper.selectList(new LambdaQueryWrapper<KeyUnit>()).stream()
                                .map(KeyUnit::getId)
                                .toList();
                case "IMPORTANT_PART" ->
                        partMapper.selectList(new LambdaQueryWrapper<ImportantPart>()).stream()
                                .map(ImportantPart::getId)
                                .toList();
                default -> List.of();
            };
        }
        Long stationDeptId = SecurityUtils.currentUser().getDeptId();
        if ("STATION".equals(type)) {
            return stationMapper
                    .selectList(
                            new LambdaQueryWrapper<PoliceStation>()
                                    .eq(PoliceStation::getDeptId, stationDeptId))
                    .stream()
                    .map(PoliceStation::getId)
                    .toList();
        }
        return jurisdictionMapper
                .selectList(
                        new LambdaQueryWrapper<TargetJurisdiction>()
                                .eq(TargetJurisdiction::getStationDeptId, stationDeptId)
                                .eq(TargetJurisdiction::getTargetType, type))
                .stream()
                .map(TargetJurisdiction::getTargetId)
                .distinct()
                .toList();
    }

    public void checkDept(Long deptId) {
        if (deptId == null || !permittedDeptIds().contains(deptId)) {
            throw new BusinessException(403, "无权访问该部门数据");
        }
    }

    public SysDept dept(Long deptId) {
        return deptId == null ? null : deptMapper.selectById(deptId);
    }

    public void checkTarget(String targetType, Long targetId) {
        if (targetId == null || !permittedTargetIds(targetType).contains(targetId)) {
            throw new BusinessException(403, "无权访问该检查对象");
        }
    }

    public void checkStationAssignments(List<Long> stationDeptIds) {
        if (hasGlobalBusinessAccess()) return;
        Long ownDeptId = SecurityUtils.currentUser().getDeptId();
        if (stationDeptIds == null
                || stationDeptIds.isEmpty()
                || stationDeptIds.stream().anyMatch(id -> !ownDeptId.equals(id))) {
            throw new BusinessException(403, "只能维护本派出所辖区内的档案");
        }
    }

    private String normalizeTargetType(String value) {
        String type = value == null ? "" : value.toUpperCase();
        if (!List.of("STATION", "KEY_UNIT", "IMPORTANT_PART").contains(type)) {
            throw new BusinessException("不支持的检查对象类型");
        }
        return type;
    }
}
