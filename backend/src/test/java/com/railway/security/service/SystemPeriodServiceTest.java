package com.railway.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.railway.security.persistence.entity.SysConfig;
import com.railway.security.persistence.mapper.ConfigMapper;
import com.railway.security.shared.web.BusinessException;
import com.railway.security.system.SystemPeriodService;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemPeriodServiceTest {
    @Test
    void defaultBoundaryStartsAtThirdQuarterOf2026() {
        ConfigMapper mapper = mock(ConfigMapper.class);
        SystemPeriodService service = new SystemPeriodService(mapper);

        assertFalse(service.includes(2026, 2));
        assertTrue(service.includes(2026, 3));
        assertEquals(List.of(3, 4), service.includedQuarters(2026, List.of(1, 2, 3, 4)));
    }

    @Test
    void configuredFourthQuarterExcludesThirdQuarter() {
        ConfigMapper mapper = mock(ConfigMapper.class);
        SysConfig config = new SysConfig();
        config.setConfigValue("2026-Q4");
        when(mapper.selectOne(any())).thenReturn(config);
        SystemPeriodService service = new SystemPeriodService(mapper);

        assertFalse(service.includes(2026, 3));
        assertTrue(service.includes(2026, 4));
    }

    @Test
    void administratorCannotMoveBoundaryBeforeInitialQuarter() {
        SystemPeriodService service = new SystemPeriodService(mock(ConfigMapper.class));

        assertThrows(BusinessException.class, () -> service.validateConfigValue("2026-Q2"));
    }
}
