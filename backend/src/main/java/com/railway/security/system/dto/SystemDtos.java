package com.railway.security.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class SystemDtos {
    private SystemDtos() {}

    public record AdminOverviewResponse(
            Map<String, Long> deptCounts,
            Map<String, Long> archiveCounts,
            Map<String, Long> accountCovered,
            long accountCount,
            long missingAccountCount,
            Map<String, Long> relationCounts,
            Map<String, Long> sharedTargetCounts,
            Map<String, Long> unassignedArchiveCounts) {}

    public record DepartmentNode(
            Long id,
            Long parentId,
            String label,
            String deptType,
            Integer status,
            List<DepartmentNode> children) {}

    public record DepartmentResponse(
            Long id,
            Long parentId,
            String ancestors,
            String deptName,
            String deptType,
            String leader,
            String phone,
            String address,
            String jurisdiction,
            Integer sortNo,
            Integer status,
            Integer archived,
            LocalDateTime createTime,
            LocalDateTime updateTime,
            String remark) {}

    public record DepartmentSaveRequest(
            @NotNull Long parentId,
            @NotBlank @Size(max = 100) String deptName,
            @NotBlank String deptType,
            String leader,
            String phone,
            String address,
            String jurisdiction,
            Integer sortNo,
            Integer status,
            String remark) {}

    public record UserResponse(
            Long id,
            Long deptId,
            String deptName,
            String username,
            Integer status,
            String dataScope,
            List<String> roles,
            LocalDateTime lastLoginTime,
            LocalDateTime createTime) {}

    public record UserFormResponse(
            Long id,
            Long deptId,
            String username,
            Integer status,
            String dataScope,
            List<Long> roleIds,
            String accountType,
            Long parentDeptId) {}

    public record UserSaveRequest(
            @NotNull Long deptId,
            Long parentDeptId,
            String accountType,
            @Size(max = 50) String username,
            String password,
            Integer status,
            String dataScope,
            @NotEmpty List<Long> roleIds) {}

    public record RoleResponse(
            Long id,
            String roleName,
            String roleCode,
            String dataScope,
            Integer status,
            LocalDateTime createTime,
            LocalDateTime updateTime,
            String remark) {}

    public record RoleSaveRequest(
            @NotBlank @Size(max = 50) String roleName,
            @NotBlank @Size(max = 50) String roleCode,
            String dataScope,
            Integer status,
            String remark,
            List<Long> menuIds) {}

    public record RoleDetailResponse(RoleResponse role, List<Long> menuIds) {}

    public record MenuResponse(
            Long id,
            Long parentId,
            String menuName,
            String menuType,
            String path,
            String component,
            String permission,
            String icon,
            Integer sortNo,
            Integer visible,
            Integer status,
            String remark) {}

    public record MenuSaveRequest(
            Long parentId,
            @NotBlank @Size(max = 50) String menuName,
            @NotBlank String menuType,
            String path,
            String component,
            String permission,
            String icon,
            Integer sortNo,
            Integer visible,
            Integer status,
            String remark) {}

    public record ConfigResponse(
            Long id,
            String configName,
            String configKey,
            String configValue,
            String valueType,
            Integer systemFlag,
            String remark) {}

    public record ConfigUpdateRequest(
            @NotNull Long id, @NotNull String configValue, String remark) {}

    public record DictionaryTypeResponse(
            Long id, String dictName, String dictType, Integer status, String remark) {}

    public record DictionaryTypeSaveRequest(
            @NotBlank @Size(max = 100) String dictName,
            @NotBlank @Size(max = 100) String dictType,
            Integer status,
            String remark) {}

    public record DictionaryDataResponse(
            Long id,
            String dictType,
            String dictLabel,
            String dictValue,
            Integer sortNo,
            Integer status,
            String colorType,
            String remark) {}

    public record DictionaryDataSaveRequest(
            @NotBlank String dictType,
            @NotBlank @Size(max = 100) String dictLabel,
            @NotBlank @Size(max = 100) String dictValue,
            Integer sortNo,
            Integer status,
            String colorType,
            String remark) {}

    public record OperationLogResponse(
            Long id,
            Long userId,
            String username,
            Long deptId,
            String operationType,
            String module,
            String content,
            String requestMethod,
            String requestUri,
            String requestIp,
            Integer result,
            String failureReason,
            Long costTime,
            LocalDateTime createTime) {}

    public record LoginLogResponse(
            Long id,
            String username,
            String loginIp,
            String browser,
            String os,
            Integer status,
            String message,
            LocalDateTime loginTime) {}

    public record StatusUpdateRequest(@NotNull Integer status) {}

    public record PasswordResetRequest(String password) {}
}
