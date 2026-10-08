package com.railway.security.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.PoliceStation;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.entity.TargetJurisdiction;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.PoliceStationMapper;
import com.railway.security.persistence.mapper.TargetJurisdictionMapper;
import com.railway.security.persistence.mapper.UserMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.system.dto.SystemDtos.DepartmentNode;
import com.railway.security.system.dto.SystemDtos.DepartmentResponse;
import com.railway.security.system.dto.SystemDtos.DepartmentSaveRequest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartmentAdministrationService {
    private final DeptMapper deptMapper;
    private final UserMapper userMapper;
    private final PoliceStationMapper stationMapper;
    private final TargetJurisdictionMapper jurisdictionMapper;
    private final DataScopeService dataScopeService;
    private final SystemDtoMapper dtoMapper;

    @Transactional(readOnly = true)
    public List<DepartmentNode> tree() {
        List<SysDept> departments =
                deptMapper.selectList(
                        new LambdaQueryWrapper<SysDept>()
                                .in(SysDept::getId, safeIds(dataScopeService.permittedDeptIds()))
                                .orderByAsc(SysDept::getSortNo));
        Set<Long> visibleIds = departments.stream().map(SysDept::getId).collect(Collectors.toSet());
        return departments.stream()
                .filter(
                        department ->
                                Long.valueOf(0L).equals(department.getParentId())
                                        || !visibleIds.contains(department.getParentId()))
                .sorted(Comparator.comparing(SysDept::getSortNo))
                .map(department -> toNode(department, departments))
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse get(Long id) {
        dataScopeService.checkDept(id);
        return dtoMapper.toDepartmentResponse(required(id));
    }

    @Transactional
    public DepartmentResponse create(DepartmentSaveRequest request) {
        SysDept parent = required(request.parentId());
        dataScopeService.checkDept(parent.getId());
        assertHierarchy(parent.getDeptType(), request.deptType());
        SysDept department = new SysDept();
        copy(request, department);
        department.setAncestors(parent.getAncestors() + "," + parent.getId());
        department.setStatus(request.status() == null ? 1 : request.status());
        department.setArchived(0);
        deptMapper.insert(department);
        return dtoMapper.toDepartmentResponse(department);
    }

    @Transactional
    public void update(Long id, DepartmentSaveRequest request) {
        SysDept existing = required(id);
        dataScopeService.checkDept(id);
        if (Long.valueOf(1L).equals(id)) {
            copyEditableRootFields(request, existing);
            deptMapper.updateById(existing);
            return;
        }
        if (id.equals(request.parentId())) {
            throw new BusinessException("上级部门不能选择本部门");
        }
        SysDept parent = required(request.parentId());
        dataScopeService.checkDept(parent.getId());
        if (parent.getAncestors() != null
                && Arrays.asList(parent.getAncestors().split(",")).contains(String.valueOf(id))) {
            throw new BusinessException("上级部门不能选择本部门的下级");
        }
        assertHierarchy(parent.getDeptType(), request.deptType());
        String oldPath = existing.getAncestors() + "," + existing.getId();
        String newAncestors = parent.getAncestors() + "," + parent.getId();
        String newPath = newAncestors + "," + existing.getId();
        copy(request, existing);
        existing.setParentId(parent.getId());
        existing.setAncestors(newAncestors);
        deptMapper.updateById(existing);
        if (!oldPath.equals(newPath)) {
            deptMapper
                    .selectList(
                            new LambdaQueryWrapper<SysDept>()
                                    .apply("FIND_IN_SET({0}, ancestors)", id))
                    .forEach(
                            child -> {
                                child.setAncestors(
                                        child.getAncestors()
                                                .replaceFirst(Pattern.quote(oldPath), newPath));
                                deptMapper.updateById(child);
                            });
        }
    }

    @Transactional
    public void delete(Long id) {
        if (Long.valueOf(1L).equals(id)) {
            throw new BusinessException("根部门不能删除");
        }
        dataScopeService.checkDept(id);
        required(id);
        if (deptMapper.selectCount(new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id))
                > 0) {
            throw new BusinessException("存在下级部门，不能删除");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeptId, id))
                > 0) {
            throw new BusinessException("部门下存在账号，不能删除");
        }
        if (stationMapper.selectCount(
                                new LambdaQueryWrapper<PoliceStation>()
                                        .eq(PoliceStation::getDeptId, id))
                        > 0
                || jurisdictionMapper.selectCount(
                                new LambdaQueryWrapper<TargetJurisdiction>()
                                        .eq(TargetJurisdiction::getStationDeptId, id))
                        > 0) {
            throw new BusinessException("部门仍关联派出所档案或辖区数据，不能删除");
        }
        deptMapper.deleteById(id);
    }

    @Transactional
    public void changeStatus(Long id, Integer status) {
        if (!List.of(0, 1).contains(status)) {
            throw new BusinessException("部门状态无效");
        }
        dataScopeService.checkDept(id);
        SysDept department = required(id);
        department.setStatus(status);
        deptMapper.updateById(department);
    }

    private DepartmentNode toNode(SysDept department, List<SysDept> departments) {
        List<DepartmentNode> children =
                departments.stream()
                        .filter(candidate -> department.getId().equals(candidate.getParentId()))
                        .sorted(Comparator.comparing(SysDept::getSortNo))
                        .map(candidate -> toNode(candidate, departments))
                        .toList();
        return new DepartmentNode(
                department.getId(),
                department.getParentId(),
                department.getDeptName(),
                department.getDeptType(),
                department.getStatus(),
                children);
    }

    private SysDept required(Long id) {
        SysDept department = deptMapper.selectById(id);
        if (department == null) {
            throw new BusinessException("部门不存在");
        }
        return department;
    }

    private void copy(DepartmentSaveRequest request, SysDept target) {
        target.setParentId(request.parentId());
        target.setDeptName(request.deptName().trim());
        target.setDeptType(request.deptType());
        target.setLeader(request.leader());
        target.setPhone(request.phone());
        target.setAddress(request.address());
        target.setJurisdiction(request.jurisdiction());
        target.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        target.setStatus(request.status() == null ? 1 : request.status());
        target.setRemark(request.remark());
    }

    private void copyEditableRootFields(DepartmentSaveRequest request, SysDept target) {
        target.setDeptName(request.deptName().trim());
        target.setLeader(request.leader());
        target.setPhone(request.phone());
        target.setAddress(request.address());
        target.setJurisdiction(request.jurisdiction());
        target.setSortNo(request.sortNo());
        target.setStatus(request.status());
        target.setRemark(request.remark());
    }

    private void assertHierarchy(String parentType, String childType) {
        if (!("BUREAU".equals(parentType) && "STATION".equals(childType))) {
            throw new BusinessException("部门层级关系无效");
        }
    }

    private List<Long> safeIds(List<Long> ids) {
        return ids == null || ids.isEmpty() ? List.of(-1L) : ids;
    }
}
