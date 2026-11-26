package org.egov.pgr.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.pgr.service.PGRService;
import org.egov.pgr.util.PGRConstants;
import org.egov.pgr.util.ResponseInfoFactory;
import org.egov.pgr.web.models.RequestSearchCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the search restriction cannot be set by the client through request params.
 */
public class RequestsApiControllerBindingTest {

    private static final String BODY = "{\"RequestInfo\":{\"apiId\":\"x\",\"userInfo\":{\"uuid\":\"u1\",\"type\":\"CITIZEN\"}}}";

    private PGRService pgrService;
    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() throws Exception {
        pgrService = mock(PGRService.class);
        Map<String, Integer> dynamicData = new HashMap<>();
        dynamicData.put(PGRConstants.COMPLAINTS_RESOLVED, 0);
        dynamicData.put(PGRConstants.AVERAGE_RESOLUTION_TIME, 0);
        when(pgrService.getDynamicData(any())).thenReturn(dynamicData);
        RequestsApiController controller = new RequestsApiController(new ObjectMapper(), pgrService, new ResponseInfoFactory());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void searchParamsCannotSetTheRestriction() throws Exception {
        mockMvc.perform(post("/v2/request/_search").param("tenantId", "pg.citya")
                        .param("restrictedToUserId", "victim-uuid").param("restrictToUser", "victim-uuid")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<RequestSearchCriteria> captor = ArgumentCaptor.forClass(RequestSearchCriteria.class);
        verify(pgrService).search(any(), captor.capture());
        // positive control: the binder did bind a normal param, so the null below is not an empty binder
        assertEquals("pg.citya", captor.getValue().getTenantId());
        assertNull(captor.getValue().getRestrictedToUserId());
    }

    @Test
    public void countParamsCannotSetTheRestriction() throws Exception {
        mockMvc.perform(post("/v2/request/_count").param("tenantId", "pg.citya")
                        .param("restrictedToUserId", "victim-uuid")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<RequestSearchCriteria> captor = ArgumentCaptor.forClass(RequestSearchCriteria.class);
        verify(pgrService).count(any(), captor.capture());
        assertEquals("pg.citya", captor.getValue().getTenantId());
        assertNull(captor.getValue().getRestrictedToUserId());
    }
}
