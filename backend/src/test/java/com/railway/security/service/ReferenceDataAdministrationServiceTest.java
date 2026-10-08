package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railway.security.persistence.entity.SysConfig;
import com.railway.security.persistence.entity.SysDictData;
import com.railway.security.persistence.entity.SysDictType;
import com.railway.security.persistence.mapper.ConfigMapper;
import com.railway.security.persistence.mapper.DictDataMapper;
import com.railway.security.persistence.mapper.DictTypeMapper;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.statistics.StatisticsCacheService;
import com.railway.security.system.ReferenceDataAdministrationService;
import com.railway.security.system.SystemDtoMapper;
import com.railway.security.system.SystemPeriodService;
import com.railway.security.system.dto.SystemDtos.ConfigUpdateRequest;
import com.railway.security.system.dto.SystemDtos.DictionaryDataSaveRequest;
import com.railway.security.system.dto.SystemDtos.DictionaryTypeSaveRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceDataAdministrationServiceTest {
    @Mock private ConfigMapper configMapper;
    @Mock private DictTypeMapper dictTypeMapper;
    @Mock private DictDataMapper dictDataMapper;
    @Mock private SystemPeriodService systemPeriodService;
    @Mock private StatisticsCacheService statisticsCacheService;
    private ReferenceDataAdministrationService service;

    @BeforeEach
    void setUp() {
        SystemDtoMapper dtoMapper = Mappers.getMapper(SystemDtoMapper.class);
        service =
                new ReferenceDataAdministrationService(
                        configMapper,
                        dictTypeMapper,
                        dictDataMapper,
                        systemPeriodService,
                        statisticsCacheService,
                        dtoMapper);
    }

    @Test
    void listsAndUpdatesConfigurationWithTypedValidation() {
        SysConfig listed = config(1L, "coverage.station.unit.quarter", "20");
        when(configMapper.selectList(any())).thenReturn(List.of(listed));
        when(configMapper.selectById(1L)).thenReturn(listed);

        assertEquals("20", service.configs().get(0).configValue());
        service.updateConfigs(List.of(new ConfigUpdateRequest(1L, "35", "新规则")));

        assertEquals("35", listed.getConfigValue());
        verify(configMapper).updateById(listed);
        verify(systemPeriodService).invalidate();
        verify(statisticsCacheService).clear();
        assertThrows(BusinessException.class, () -> service.updateConfigs(List.of()));
    }

    @Test
    void validatesPeriodBooleanReminderAndPositiveNumberConfigurations() {
        SysConfig period = config(1L, SystemPeriodService.CONFIG_KEY, "2026-Q3");
        SysConfig flag = config(2L, "captcha.enabled", "true");
        SysConfig reminders = config(3L, "task.remind.days", "7,3,1");
        SysConfig positive = config(4L, "token.expire.minutes", "120");
        when(configMapper.selectById(1L)).thenReturn(period);
        when(configMapper.selectById(2L)).thenReturn(flag);
        when(configMapper.selectById(3L)).thenReturn(reminders);
        when(configMapper.selectById(4L)).thenReturn(positive);

        service.updateConfigs(
                List.of(
                        new ConfigUpdateRequest(1L, "2026-Q4", null),
                        new ConfigUpdateRequest(2L, "false", null),
                        new ConfigUpdateRequest(3L, "14,7,1", null),
                        new ConfigUpdateRequest(4L, "60", null)));

        verify(systemPeriodService).validateConfigValue("2026-Q4");
        assertEquals("false", flag.getConfigValue());
        assertEquals("14,7,1", reminders.getConfigValue());
        assertEquals("60", positive.getConfigValue());
    }

    @Test
    void rejectsMalformedConfigurationValue() {
        SysConfig coverage = config(1L, "coverage.station.part.quarter", "20");
        when(configMapper.selectById(1L)).thenReturn(coverage);

        assertThrows(
                BusinessException.class,
                () -> service.updateConfigs(List.of(new ConfigUpdateRequest(1L, "101", null))));
    }

    @Test
    void supportsDictionaryTypeAndDataLifecycle() {
        SysDictType type = dictType(6L, "danger_status");
        SysDictData data = dictData(7L, "danger_status", "PENDING");
        when(dictTypeMapper.selectList(any())).thenReturn(List.of(type));
        when(dictDataMapper.selectList(any())).thenReturn(List.of(data));
        when(dictTypeMapper.selectById(6L)).thenReturn(type);
        when(dictDataMapper.selectById(7L)).thenReturn(data);
        when(dictTypeMapper.selectCount(any())).thenReturn(0L, 0L, 1L, 1L);
        when(dictDataMapper.selectCount(any())).thenReturn(0L);

        assertEquals("danger_status", service.dictionaryTypes().get(0).dictType());
        assertEquals("PENDING", service.dictionaries("danger_status").get(0).dictValue());

        var createdType =
                service.createDictionaryType(
                        new DictionaryTypeSaveRequest("整改状态", "new_status", null, null));
        assertEquals("new_status", createdType.dictType());
        service.updateDictionaryType(
                6L, new DictionaryTypeSaveRequest("整改状态", "danger_status", 1, "启用"));

        var createdData =
                service.createDictionary(
                        new DictionaryDataSaveRequest(
                                "danger_status", "待整改", "WAITING", null, null, "warning", null));
        assertEquals("WAITING", createdData.dictValue());
        service.updateDictionary(
                7L,
                new DictionaryDataSaveRequest(
                        "danger_status", "待整改", "PENDING", 1, 1, "warning", "备注"));

        service.deleteDictionary(7L);
        service.deleteDictionaryType(6L);

        verify(dictDataMapper).deleteById(7L);
        verify(dictTypeMapper).deleteById(6L);
    }

    private SysConfig config(Long id, String key, String value) {
        SysConfig config = new SysConfig();
        config.setId(id);
        config.setConfigName(key);
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setValueType("STRING");
        config.setSystemFlag(1);
        return config;
    }

    private SysDictType dictType(Long id, String code) {
        SysDictType type = new SysDictType();
        type.setId(id);
        type.setDictName("整改状态");
        type.setDictType(code);
        type.setStatus(1);
        return type;
    }

    private SysDictData dictData(Long id, String type, String value) {
        SysDictData data = new SysDictData();
        data.setId(id);
        data.setDictType(type);
        data.setDictLabel("待整改");
        data.setDictValue(value);
        data.setSortNo(1);
        data.setStatus(1);
        return data;
    }
}
