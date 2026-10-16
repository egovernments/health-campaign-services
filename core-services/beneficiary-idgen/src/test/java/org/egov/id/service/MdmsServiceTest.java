package org.egov.id.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.models.idgen.IdRequest;
import org.egov.id.config.PropertiesManager;
import org.egov.mdms.model.MasterDetail;
import org.egov.mdms.model.MdmsResponse;
import org.egov.mdms.service.MdmsClientService;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import net.minidev.json.JSONArray;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ContextConfiguration(classes = {MdmsService.class})
@ExtendWith(SpringExtension.class)
class MdmsServiceTest {
    @MockBean
    private MdmsClientService mdmsClientService;

    @MockBean
    private PropertiesManager propertiesManager;

    @Autowired
    private MdmsService mdmsService;

    @Test
    void testGetMasterData() {
        MdmsResponse mdmsResponse = new MdmsResponse();
        when(this.mdmsClientService.getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, List<MasterDetail>>) any())).thenReturn(mdmsResponse);
        RequestInfo requestInfo = new RequestInfo();
        assertSame(mdmsResponse, this.mdmsService.getMasterData(requestInfo, "42", new HashMap<>()));
        verify(this.mdmsClientService).getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, List<MasterDetail>>) any());
    }

    @Test
    void testGetMasterData2() {
        MdmsResponse mdmsResponse = new MdmsResponse();
        when(this.mdmsClientService.getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, List<MasterDetail>>) any())).thenReturn(mdmsResponse);
        assertSame(mdmsResponse, this.mdmsService.getMasterData(null, "42", new HashMap<>()));
        verify(this.mdmsClientService).getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, List<MasterDetail>>) any());
    }

    @Test
    void testGetMasterData4() {
        when(this.mdmsClientService.getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, List<MasterDetail>>) any())).thenThrow(new CustomException("Code", "An error occurred"));
        RequestInfo requestInfo = new RequestInfo();
        assertThrows(CustomException.class, () -> this.mdmsService.getMasterData(requestInfo, "42", new HashMap<>()));
        verify(this.mdmsClientService).getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, List<MasterDetail>>) any());
    }

    @Test
    void testGetCity() {
        when(this.mdmsClientService.getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any()))
                .thenReturn(new MdmsResponse());
        RequestInfo requestInfo = new RequestInfo();
        assertThrows(CustomException.class, () -> this.mdmsService.getCity(requestInfo, new IdRequest()));
        verify(this.mdmsClientService).getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any());
    }

    @Test
    void testGetCity2() {
        when(this.mdmsClientService.getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any())).thenReturn(null);
        RequestInfo requestInfo = new RequestInfo();
        assertThrows(CustomException.class, () -> this.mdmsService.getCity(requestInfo, new IdRequest()));
        verify(this.mdmsClientService).getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any());
    }

    @Test
    void testGetCity3() {
        ResponseInfo responseInfo = new ResponseInfo();
        when(this.mdmsClientService.getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any()))
                .thenReturn(new MdmsResponse(responseInfo, new HashMap<>()));
        RequestInfo requestInfo = new RequestInfo();
        assertThrows(CustomException.class, () -> this.mdmsService.getCity(requestInfo, new IdRequest()));
        verify(this.mdmsClientService).getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any());
    }

    @Test
    void testGetCity4() {
        when(this.mdmsClientService.getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any()))
                .thenReturn(new MdmsResponse());
        assertThrows(CustomException.class, () -> this.mdmsService.getCity(null, new IdRequest()));
        verify(this.mdmsClientService).getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any());
    }

    @Test
    void testGetCity5() {
        when(this.mdmsClientService.getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any()))
                .thenReturn(new MdmsResponse());
        RequestInfo requestInfo = mock(RequestInfo.class);
        assertThrows(CustomException.class, () -> this.mdmsService.getCity(requestInfo, new IdRequest()));
    }

    @Test
    void testGetCity7() {
        when(this.mdmsClientService.getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any()))
                .thenThrow(new CustomException("tenants", "An error occurred"));
        RequestInfo requestInfo = new RequestInfo();
        assertThrows(CustomException.class, () -> this.mdmsService.getCity(requestInfo, new IdRequest()));
        verify(this.mdmsClientService).getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any());
    }

    @Test
    void testGetIdFormat() {
        when(this.mdmsClientService.getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any()))
                .thenReturn(new MdmsResponse());
        RequestInfo requestInfo = new RequestInfo();
        assertNull(this.mdmsService.getIdFormat(requestInfo, new IdRequest()));
        verify(this.mdmsClientService).getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any());
    }

    @Test
    void testGetIdFormat2() {
        when(this.mdmsClientService.getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any())).thenReturn(null);
        RequestInfo requestInfo = new RequestInfo();
        assertThrows(CustomException.class, () -> this.mdmsService.getIdFormat(requestInfo, new IdRequest()));
        verify(this.mdmsClientService).getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any());
    }

    @Test
    void testGetIdFormat3() {
        ResponseInfo responseInfo = new ResponseInfo();
        when(this.mdmsClientService.getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any()))
                .thenReturn(new MdmsResponse(responseInfo, new HashMap<>()));
        RequestInfo requestInfo = new RequestInfo();
        assertNull(this.mdmsService.getIdFormat(requestInfo, new IdRequest()));
        verify(this.mdmsClientService).getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any());
    }

    @Test
    void testGetIdFormat4() {
        when(this.mdmsClientService.getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any()))
                .thenReturn(new MdmsResponse());
        assertNull(this.mdmsService.getIdFormat(null, new IdRequest()));
        verify(this.mdmsClientService).getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any());
    }

    @Test
    void testGetIdFormat7() {
        when(this.mdmsClientService.getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any()))
                .thenThrow(new CustomException("tenants", "An error occurred"));
        RequestInfo requestInfo = new RequestInfo();
        assertThrows(CustomException.class, () -> this.mdmsService.getIdFormat(requestInfo, new IdRequest()));
        verify(this.mdmsClientService).getMaster((org.egov.common.contract.request.RequestInfo) any(), (String) any(),
                (java.util.Map<String, java.util.List<org.egov.mdms.model.MasterDetail>>) any());
    }

    @Test
    void testGetDispatchLimitConfigReturnsEmptyWhenNoData() {
        when(propertiesManager.getMdmsDispatchLimitModule()).thenReturn("beneficiary-idgen");
        when(propertiesManager.getMdmsDispatchLimitMaster()).thenReturn("IdDispatchConfig");
        when(this.mdmsClientService.getMaster(any(), any(), any())).thenReturn(new MdmsResponse(new ResponseInfo(), new HashMap<>()));

        Optional<?> result = this.mdmsService.getDispatchLimitConfig(new RequestInfo(), "ch");

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetDispatchLimitConfigParsesDataAndAppliesDefaults() {
        when(propertiesManager.getMdmsDispatchLimitModule()).thenReturn("beneficiary-idgen");
        when(propertiesManager.getMdmsDispatchLimitMaster()).thenReturn("IdDispatchConfig");
        // Defaults from PropertiesManager for the fields deliberately OMITTED from the MDMS record below.
        when(propertiesManager.getDispatchLimitUserDeviceTotal()).thenReturn(10000);
        when(propertiesManager.isDispatchLimitUserDevicePerDayEnabled()).thenReturn(true);
        when(propertiesManager.isIdDispatchRetrievalRestrictToTodayEnabled()).thenReturn(true);

        // MDMS record supplies only a subset of fields; totalLimit, perDayEnabled and restrictToTodayEnabled
        // are omitted so the parser must fall back to the PropertiesManager defaults for those.
        Map<String, Object> config = new HashMap<>();
        config.put("tenantId", "ch");
        config.put("perDayLimit", 50);
        config.put("perDayExpireDays", 20);
        config.put("totalExpireDays", 40);

        JSONArray masterList = new JSONArray();
        masterList.add(config);
        Map<String, JSONArray> moduleMap = new HashMap<>();
        moduleMap.put("IdDispatchConfig", masterList);
        Map<String, Map<String, JSONArray>> mdmsRes = new HashMap<>();
        mdmsRes.put("beneficiary-idgen", moduleMap);

        when(this.mdmsClientService.getMaster(any(), any(), any()))
                .thenReturn(new MdmsResponse(new ResponseInfo(), (Map) mdmsRes));

        Optional<org.egov.id.model.DispatchLimitConfig> result = this.mdmsService.getDispatchLimitConfig(new RequestInfo(), "ch");

        assertTrue(result.isPresent());
        // Explicitly supplied values are parsed as-is.
        assertEquals(50, result.get().getPerDayLimit());
        assertEquals(20, result.get().getPerDayExpireDays());
        assertEquals(40, result.get().getTotalExpireDays());
        // Omitted fields fall back to the PropertiesManager defaults.
        assertEquals(10000, result.get().getTotalLimit());
        assertTrue(result.get().isPerDayEnabled());
        assertTrue(result.get().isRestrictToTodayEnabled());
    }

    @Test
    void testGetIdPoolConfigParsesData() {
        when(propertiesManager.getDefaultIdPoolConfig()).thenReturn(org.egov.id.model.IdPoolConfig.builder()
                .seqCode("id.pool.number")
                .paddingLength(12)
                .build());

        Map<String, Object> config = new HashMap<>();
        config.put("tenantId", "ch");
        config.put("seqCode", "id.pool.number");
        config.put("paddingLength", 14);

        JSONArray masterList = new JSONArray();
        masterList.add(config);
        Map<String, JSONArray> moduleMap = new HashMap<>();
        moduleMap.put("IdPoolConfig", masterList);
        Map<String, Map<String, JSONArray>> mdmsRes = new HashMap<>();
        mdmsRes.put("beneficiary-idgen", moduleMap);

        when(this.mdmsClientService.getMaster(any(), any(), any()))
                .thenReturn(new MdmsResponse(new ResponseInfo(), (Map) mdmsRes));

        Optional<org.egov.id.model.IdPoolConfig> result = this.mdmsService.getIdPoolConfig(new RequestInfo(), "ch");

        assertTrue(result.isPresent());
        assertEquals("id.pool.number", result.get().getSeqCode());
        assertEquals(14, result.get().getPaddingLength());
    }

    @Test
    void testGetIdPoolConfigReturnsEmptyWhenNoData() {
        when(this.mdmsClientService.getMaster(any(), any(), any()))
                .thenReturn(new MdmsResponse(new ResponseInfo(), new HashMap<>()));

        Optional<org.egov.id.model.IdPoolConfig> result = this.mdmsService.getIdPoolConfig(new RequestInfo(), "ch");

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetIdPoolConfigPropagatesExceptionWhenMdmsThrows() {
        when(propertiesManager.getMdmsIdPoolModule()).thenReturn("beneficiary-idgen");
        when(propertiesManager.getMdmsIdPoolMaster()).thenReturn("IdPoolConfig");
        when(this.mdmsClientService.getMaster(any(), any(), any()))
                .thenThrow(new RuntimeException("MDMS service unavailable"));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> this.mdmsService.getIdPoolConfig(new RequestInfo(), "ch"));

        assertEquals("MDMS service unavailable", thrown.getMessage());
        verify(this.mdmsClientService).getMaster(any(), any(), any());
    }

    @Test
    void testGetIdPoolConfigThrowsCustomExceptionWhenMdmsThrowsCustomException() {
        when(propertiesManager.getMdmsIdPoolModule()).thenReturn("beneficiary-idgen");
        when(propertiesManager.getMdmsIdPoolMaster()).thenReturn("IdPoolConfig");
        when(this.mdmsClientService.getMaster(any(), any(), any()))
                .thenThrow(new CustomException("MDMS_ERROR", "Connection refused"));

        CustomException thrown = assertThrows(CustomException.class,
                () -> this.mdmsService.getIdPoolConfig(new RequestInfo(), "ch"));

        assertEquals("MDMS_ERROR", thrown.getCode());
    }

    @Test
    void testGetIdPoolConfigThrowsWhenTenantIdIsNull() {
        CustomException thrown = assertThrows(CustomException.class,
                () -> this.mdmsService.getIdPoolConfig(new RequestInfo(), null));

        assertEquals("INVALID_TENANT_ID", thrown.getCode());
        verifyNoInteractions(this.mdmsClientService);
    }

    @Test
    void testGetIdPoolConfigThrowsWhenTenantIdIsBlank() {
        CustomException thrown = assertThrows(CustomException.class,
                () -> this.mdmsService.getIdPoolConfig(new RequestInfo(), "   "));

        assertEquals("INVALID_TENANT_ID", thrown.getCode());
        verifyNoInteractions(this.mdmsClientService);
    }

    @Test
    void testGetIdPoolConfigThrowsWhenTenantIdContainsUnsafeCharacters() {
        CustomException thrown = assertThrows(CustomException.class,
                () -> this.mdmsService.getIdPoolConfig(new RequestInfo(), "ch' || true || '"));

        assertEquals("INVALID_TENANT_ID", thrown.getCode());
        verifyNoInteractions(this.mdmsClientService);
    }
}

