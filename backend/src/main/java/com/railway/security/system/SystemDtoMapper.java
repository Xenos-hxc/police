package com.railway.security.system;

import com.railway.security.persistence.entity.SysConfig;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.entity.SysDictData;
import com.railway.security.persistence.entity.SysDictType;
import com.railway.security.persistence.entity.SysLoginLog;
import com.railway.security.persistence.entity.SysMenu;
import com.railway.security.persistence.entity.SysOperLog;
import com.railway.security.persistence.entity.SysRole;
import com.railway.security.system.dto.SystemDtos.ConfigResponse;
import com.railway.security.system.dto.SystemDtos.DepartmentResponse;
import com.railway.security.system.dto.SystemDtos.DictionaryDataResponse;
import com.railway.security.system.dto.SystemDtos.DictionaryTypeResponse;
import com.railway.security.system.dto.SystemDtos.LoginLogResponse;
import com.railway.security.system.dto.SystemDtos.MenuResponse;
import com.railway.security.system.dto.SystemDtos.OperationLogResponse;
import com.railway.security.system.dto.SystemDtos.RoleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SystemDtoMapper {
    DepartmentResponse toDepartmentResponse(SysDept source);

    RoleResponse toRoleResponse(SysRole source);

    MenuResponse toMenuResponse(SysMenu source);

    ConfigResponse toConfigResponse(SysConfig source);

    DictionaryTypeResponse toDictionaryTypeResponse(SysDictType source);

    DictionaryDataResponse toDictionaryDataResponse(SysDictData source);

    OperationLogResponse toOperationLogResponse(SysOperLog source);

    LoginLogResponse toLoginLogResponse(SysLoginLog source);
}
