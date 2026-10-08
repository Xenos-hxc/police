package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railway.security.archive.ArchiveService;
import com.railway.security.persistence.entity.KeyUnit;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.ImportantPartMapper;
import com.railway.security.persistence.mapper.KeyUnitMapper;
import com.railway.security.persistence.mapper.PoliceStationMapper;
import com.railway.security.persistence.mapper.TargetJurisdictionMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.statistics.StatisticsCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArchiveServiceTest {
    @Mock private PoliceStationMapper stationMapper;
    @Mock private KeyUnitMapper unitMapper;
    @Mock private ImportantPartMapper partMapper;
    @Mock private TargetJurisdictionMapper jurisdictionMapper;
    @Mock private DeptMapper deptMapper;
    @Mock private CheckTaskMapper taskMapper;
    @Mock private DataScopeService dataScopeService;
    @Mock private StatisticsCacheService statisticsCacheService;

    @Test
    void createUnitGeneratesHiddenInternalCode() {
        when(dataScopeService.hasGlobalBusinessAccess()).thenReturn(true);
        KeyUnit unit = new KeyUnit();
        unit.setUnitName("新增测试单位");

        service().createUnit(unit);

        assertTrue(unit.getUnitCode().matches("KU[A-F0-9]{16}"));
        verify(unitMapper).insert(unit);
    }

    @Test
    void updateUnitWithoutVisibleCodeKeepsExistingInternalCode() {
        KeyUnit existing = new KeyUnit();
        existing.setId(8L);
        existing.setUnitCode("KU_INTERNAL_EXISTING");
        existing.setUnitName("原单位");
        existing.setStatus(1);
        when(unitMapper.selectById(8L)).thenReturn(existing);

        KeyUnit update = new KeyUnit();
        update.setUnitName("修改后的单位");
        service().updateUnit(8L, update);

        assertEquals("KU_INTERNAL_EXISTING", existing.getUnitCode());
        assertEquals("修改后的单位", existing.getUnitName());
        verify(unitMapper).updateById(existing);
    }

    private ArchiveService service() {
        return new ArchiveService(
                stationMapper,
                unitMapper,
                partMapper,
                jurisdictionMapper,
                deptMapper,
                taskMapper,
                dataScopeService,
                statisticsCacheService);
    }
}
