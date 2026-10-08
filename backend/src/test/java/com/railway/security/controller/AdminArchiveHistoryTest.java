package com.railway.security.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.railway.security.archive.ArchiveCatalogueQueryService;
import com.railway.security.archive.ArchiveHistoryService;
import com.railway.security.archive.ArchiveService;
import com.railway.security.persistence.entity.LoginUser;
import com.railway.security.persistence.entity.PoliceStation;
import com.railway.security.persistence.entity.SysUser;
import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.CheckRecordMapper;
import com.railway.security.persistence.mapper.CheckTaskMapper;
import com.railway.security.persistence.mapper.DeptMapper;
import com.railway.security.persistence.mapper.ImportantPartMapper;
import com.railway.security.persistence.mapper.KeyUnitMapper;
import com.railway.security.persistence.mapper.PoliceStationMapper;
import com.railway.security.persistence.mapper.TargetJurisdictionMapper;
import com.railway.security.shared.security.DataScopeService;
import com.railway.security.system.SystemPeriodService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AdminArchiveHistoryTest {
    @Mock private PoliceStationMapper stationMapper;
    @Mock private KeyUnitMapper unitMapper;
    @Mock private ImportantPartMapper partMapper;
    @Mock private TargetJurisdictionMapper jurisdictionMapper;
    @Mock private CheckTaskMapper taskMapper;
    @Mock private CheckRecordMapper recordMapper;
    @Mock private CheckAttachmentMapper attachmentMapper;
    @Mock private DeptMapper deptMapper;
    @Mock private DataScopeService dataScopeService;
    @Mock private ArchiveService archiveService;
    @Mock private SystemPeriodService systemPeriodService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administratorSeesTheSameArchiveHistorySectionsAsBusinessUsers() {
        loginAdmin();
        PoliceStation station = new PoliceStation();
        station.setId(10L);
        station.setDeptId(110L);
        station.setStationName("测试派出所");
        when(stationMapper.selectById(10L)).thenReturn(station);

        when(systemPeriodService.effectivePeriod())
                .thenReturn(new SystemPeriodService.Period(2026, 2));

        var result =
                service()
                        .detail(
                                "police-stations",
                                10L,
                                new com.railway.security.archive.ArchiveDtos.HistoryCriteria(
                                        2026, 2, null, null));

        assertSame(station, result.archive());
        assertTrue(result.businessHistoryVisible());
        assertEquals(List.of(), result.items());
        assertEquals(0, result.progress().total());
    }

    private ArchiveHistoryService service() {
        var catalogueService =
                new ArchiveCatalogueQueryService(
                        stationMapper,
                        unitMapper,
                        partMapper,
                        jurisdictionMapper,
                        dataScopeService,
                        archiveService);
        return new ArchiveHistoryService(
                catalogueService,
                taskMapper,
                recordMapper,
                attachmentMapper,
                deptMapper,
                systemPeriodService);
    }

    private void loginAdmin() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setDeptId(1L);
        user.setUsername("admin");
        user.setPassword("unused");
        user.setRealName("系统管理员");
        user.setStatus(1);
        user.setDataScope("ALL");
        LoginUser principal = new LoginUser(user, List.of("ADMIN"), List.of("statistics:view"));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
    }
}
