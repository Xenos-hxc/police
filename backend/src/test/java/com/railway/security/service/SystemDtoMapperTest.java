package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.railway.security.persistence.entity.SysConfig;
import com.railway.security.persistence.entity.SysDept;
import com.railway.security.persistence.entity.SysDictData;
import com.railway.security.persistence.entity.SysDictType;
import com.railway.security.persistence.entity.SysLoginLog;
import com.railway.security.persistence.entity.SysMenu;
import com.railway.security.persistence.entity.SysOperLog;
import com.railway.security.persistence.entity.SysRole;
import com.railway.security.system.SystemDtoMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class SystemDtoMapperTest {
    private final SystemDtoMapper mapper = Mappers.getMapper(SystemDtoMapper.class);

    @Test
    void mapsEverySystemEntityToItsPublicContract() {
        SysDept department = new SysDept();
        department.setId(2L);
        department.setParentId(1L);
        department.setAncestors("0,1");
        department.setDeptName("测试派出所");
        department.setDeptType("STATION");
        department.setLeader("负责人");
        department.setPhone("123");
        department.setAddress("地址");
        department.setJurisdiction("辖区");
        department.setSortNo(2);
        department.setStatus(1);
        department.setArchived(0);
        department.setCreateTime(LocalDateTime.of(2026, 9, 1, 8, 0));
        department.setUpdateTime(LocalDateTime.of(2026, 9, 2, 8, 0));
        department.setRemark("备注");
        assertEquals("测试派出所", mapper.toDepartmentResponse(department).deptName());

        SysRole role = new SysRole();
        role.setId(3L);
        role.setRoleName("派出所账号");
        role.setRoleCode("STATION");
        role.setDataScope("DEPT");
        role.setStatus(1);
        role.setRemark("角色备注");
        assertEquals("STATION", mapper.toRoleResponse(role).roleCode());

        SysMenu menu = new SysMenu();
        menu.setId(4L);
        menu.setParentId(0L);
        menu.setMenuName("任务管理");
        menu.setMenuType("C");
        menu.setPath("/tasks");
        menu.setComponent("TaskListView");
        menu.setPermission("task:list");
        menu.setIcon("Tickets");
        menu.setSortNo(1);
        menu.setVisible(1);
        menu.setStatus(1);
        menu.setRemark("菜单备注");
        assertEquals("task:list", mapper.toMenuResponse(menu).permission());

        SysConfig config = new SysConfig();
        config.setId(5L);
        config.setConfigName("覆盖率");
        config.setConfigKey("coverage.station.unit.quarter");
        config.setConfigValue("25");
        config.setValueType("NUMBER");
        config.setSystemFlag(1);
        config.setRemark("配置备注");
        assertEquals("25", mapper.toConfigResponse(config).configValue());

        SysDictType dictType = new SysDictType();
        dictType.setId(6L);
        dictType.setDictName("任务状态");
        dictType.setDictType("task_status");
        dictType.setStatus(1);
        dictType.setRemark("字典类型备注");
        assertEquals("task_status", mapper.toDictionaryTypeResponse(dictType).dictType());

        SysDictData dictData = new SysDictData();
        dictData.setId(7L);
        dictData.setDictType("task_status");
        dictData.setDictLabel("已完成");
        dictData.setDictValue("APPROVED");
        dictData.setSortNo(1);
        dictData.setStatus(1);
        dictData.setColorType("success");
        dictData.setRemark("字典数据备注");
        assertEquals("APPROVED", mapper.toDictionaryDataResponse(dictData).dictValue());

        SysOperLog operationLog = new SysOperLog();
        operationLog.setId(8L);
        operationLog.setUserId(1L);
        operationLog.setUsername("admin");
        operationLog.setDeptId(1L);
        operationLog.setOperationType("UPDATE");
        operationLog.setModule("SYSTEM");
        operationLog.setContent("更新配置");
        operationLog.setRequestMethod("PUT");
        operationLog.setRequestUri("/api/configs");
        operationLog.setRequestIp("127.0.0.1");
        operationLog.setResult(1);
        operationLog.setFailureReason(null);
        operationLog.setCostTime(12L);
        operationLog.setCreateTime(LocalDateTime.of(2026, 9, 3, 8, 0));
        assertEquals("SYSTEM", mapper.toOperationLogResponse(operationLog).module());

        SysLoginLog loginLog = new SysLoginLog();
        loginLog.setId(9L);
        loginLog.setUsername("admin");
        loginLog.setLoginIp("127.0.0.1");
        loginLog.setBrowser("Chrome");
        loginLog.setOs("Windows");
        loginLog.setStatus(1);
        loginLog.setMessage("登录成功");
        loginLog.setLoginTime(LocalDateTime.of(2026, 9, 4, 8, 0));
        assertEquals("Chrome", mapper.toLoginLogResponse(loginLog).browser());
    }
}
