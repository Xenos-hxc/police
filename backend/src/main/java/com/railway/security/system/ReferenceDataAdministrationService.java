package com.railway.security.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.railway.security.persistence.entity.SysConfig;
import com.railway.security.persistence.entity.SysDictData;
import com.railway.security.persistence.entity.SysDictType;
import com.railway.security.persistence.mapper.ConfigMapper;
import com.railway.security.persistence.mapper.DictDataMapper;
import com.railway.security.persistence.mapper.DictTypeMapper;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.statistics.StatisticsCacheService;
import com.railway.security.system.dto.SystemDtos.ConfigResponse;
import com.railway.security.system.dto.SystemDtos.ConfigUpdateRequest;
import com.railway.security.system.dto.SystemDtos.DictionaryDataResponse;
import com.railway.security.system.dto.SystemDtos.DictionaryDataSaveRequest;
import com.railway.security.system.dto.SystemDtos.DictionaryTypeResponse;
import com.railway.security.system.dto.SystemDtos.DictionaryTypeSaveRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReferenceDataAdministrationService {
    private final ConfigMapper configMapper;
    private final DictTypeMapper dictTypeMapper;
    private final DictDataMapper dictDataMapper;
    private final SystemPeriodService systemPeriodService;
    private final StatisticsCacheService statisticsCacheService;
    private final SystemDtoMapper dtoMapper;

    @Transactional(readOnly = true)
    public List<ConfigResponse> configs() {
        return configMapper
                .selectList(new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getId))
                .stream()
                .map(dtoMapper::toConfigResponse)
                .toList();
    }

    @Transactional
    public void updateConfigs(List<ConfigUpdateRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException("配置列表不能为空");
        }
        for (ConfigUpdateRequest request : requests) {
            SysConfig existing = configMapper.selectById(request.id());
            if (existing == null) {
                throw new BusinessException("配置项不存在");
            }
            validateConfig(existing.getConfigKey(), request.configValue());
            existing.setConfigValue(request.configValue());
            existing.setRemark(request.remark());
            configMapper.updateById(existing);
        }
        systemPeriodService.invalidate();
        statisticsCacheService.clear();
    }

    @Transactional(readOnly = true)
    public List<DictionaryTypeResponse> dictionaryTypes() {
        return dictTypeMapper
                .selectList(new LambdaQueryWrapper<SysDictType>().orderByAsc(SysDictType::getId))
                .stream()
                .map(dtoMapper::toDictionaryTypeResponse)
                .toList();
    }

    @Transactional
    public DictionaryTypeResponse createDictionaryType(DictionaryTypeSaveRequest request) {
        if (dictTypeMapper.selectCount(
                        new LambdaQueryWrapper<SysDictType>()
                                .eq(SysDictType::getDictType, request.dictType().trim()))
                > 0) {
            throw new BusinessException("字典类型编码已存在");
        }
        SysDictType type = new SysDictType();
        copy(request, type);
        dictTypeMapper.insert(type);
        return dtoMapper.toDictionaryTypeResponse(type);
    }

    @Transactional
    public void updateDictionaryType(Long id, DictionaryTypeSaveRequest request) {
        SysDictType existing = requiredType(id);
        if (dictTypeMapper.selectCount(
                        new LambdaQueryWrapper<SysDictType>()
                                .eq(SysDictType::getDictType, request.dictType().trim())
                                .ne(SysDictType::getId, id))
                > 0) {
            throw new BusinessException("字典类型编码已存在");
        }
        copy(request, existing);
        dictTypeMapper.updateById(existing);
    }

    @Transactional
    public void deleteDictionaryType(Long id) {
        SysDictType type = requiredType(id);
        dictDataMapper.delete(
                new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getDictType, type.getDictType()));
        dictTypeMapper.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<DictionaryDataResponse> dictionaries(String type) {
        return dictDataMapper
                .selectList(
                        new LambdaQueryWrapper<SysDictData>()
                                .eq(type != null && !type.isBlank(), SysDictData::getDictType, type)
                                .eq(SysDictData::getStatus, 1)
                                .orderByAsc(SysDictData::getSortNo))
                .stream()
                .map(dtoMapper::toDictionaryDataResponse)
                .toList();
    }

    @Transactional
    public DictionaryDataResponse createDictionary(DictionaryDataSaveRequest request) {
        requireDictionaryType(request.dictType());
        assertDictionaryValueUnique(request.dictType(), request.dictValue(), null);
        SysDictData data = new SysDictData();
        copy(request, data);
        dictDataMapper.insert(data);
        return dtoMapper.toDictionaryDataResponse(data);
    }

    @Transactional
    public void updateDictionary(Long id, DictionaryDataSaveRequest request) {
        SysDictData existing = requiredData(id);
        requireDictionaryType(request.dictType());
        assertDictionaryValueUnique(request.dictType(), request.dictValue(), id);
        copy(request, existing);
        dictDataMapper.updateById(existing);
    }

    @Transactional
    public void deleteDictionary(Long id) {
        requiredData(id);
        dictDataMapper.deleteById(id);
    }

    private void copy(DictionaryTypeSaveRequest request, SysDictType target) {
        target.setDictName(request.dictName().trim());
        target.setDictType(request.dictType().trim());
        target.setStatus(request.status() == null ? 1 : request.status());
        target.setRemark(request.remark());
    }

    private void copy(DictionaryDataSaveRequest request, SysDictData target) {
        target.setDictType(request.dictType().trim());
        target.setDictLabel(request.dictLabel().trim());
        target.setDictValue(request.dictValue().trim());
        target.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        target.setStatus(request.status() == null ? 1 : request.status());
        target.setColorType(request.colorType());
        target.setRemark(request.remark());
    }

    private void validateConfig(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("配置值不能为空");
        }
        if (SystemPeriodService.CONFIG_KEY.equals(key)) {
            systemPeriodService.validateConfigValue(value);
            return;
        }
        try {
            if (key.startsWith("coverage.")) {
                int number = Integer.parseInt(value);
                if (number < 0 || number > 100) {
                    throw new NumberFormatException();
                }
            } else if (List.of(
                            "task.default.deadline.days",
                            "danger.default.deadline.days",
                            "upload.video.max.mb",
                            "upload.file.max.mb",
                            "upload.deleted.retention.days",
                            "token.expire.minutes")
                    .contains(key)) {
                if (Long.parseLong(value) < 1) {
                    throw new NumberFormatException();
                }
            } else if (key.endsWith(".enabled") || key.startsWith("task.allow.")) {
                if (!List.of("true", "false").contains(value.toLowerCase())) {
                    throw new NumberFormatException();
                }
            } else if ("task.remind.days".equals(key)) {
                for (String day : value.split(",")) {
                    if (Integer.parseInt(day.trim()) < 0) {
                        throw new NumberFormatException();
                    }
                }
            }
        } catch (NumberFormatException exception) {
            throw new BusinessException("配置项“" + key + "”的值格式无效");
        }
    }

    private void assertDictionaryValueUnique(String type, String value, Long currentId) {
        LambdaQueryWrapper<SysDictData> query =
                new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getDictType, type.trim())
                        .eq(SysDictData::getDictValue, value.trim())
                        .ne(currentId != null, SysDictData::getId, currentId);
        if (dictDataMapper.selectCount(query) > 0) {
            throw new BusinessException("同一字典类型下的字典值不能重复");
        }
    }

    private SysDictType requiredType(Long id) {
        SysDictType type = dictTypeMapper.selectById(id);
        if (type == null) {
            throw new BusinessException("字典类型不存在");
        }
        return type;
    }

    private SysDictData requiredData(Long id) {
        SysDictData data = dictDataMapper.selectById(id);
        if (data == null) {
            throw new BusinessException("字典数据不存在");
        }
        return data;
    }

    private void requireDictionaryType(String type) {
        if (dictTypeMapper.selectCount(
                        new LambdaQueryWrapper<SysDictType>()
                                .eq(SysDictType::getDictType, type.trim()))
                == 0) {
            throw new BusinessException("字典类型不存在");
        }
    }
}
