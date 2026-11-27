package org.egov.id.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.common.models.idgen.IDPoolGenerationResponse;
import org.egov.common.models.idgen.IdDispatchResponse;
import org.egov.id.config.PropertiesManager;
import org.egov.id.exception.GlobalExceptionHandler;
import org.egov.id.producer.IdGenProducer;
import org.egov.id.service.IdDispatchService;
import org.egov.id.service.IdGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant access rule on the HTTP endpoints. Requests go through MockMvc with the service's own
 * GlobalExceptionHandler, so the assertions show what the caller receives on rejection.
 */
@ExtendWith(MockitoExtension.class)
class IdGenerationControllerTenantAccessTest {

    private static final String DENIED_MESSAGE = "User does not have access to the requested tenant";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private IdGenerationService idGenerationService;

    @Mock
    private IdDispatchService idDispatchService;

    @Mock
    private HttpServletRequest servletRequest;

    @Mock
    private PropertiesManager propertiesManager;

    @Mock
    private IdGenProducer producer;

    @InjectMocks
    private IdGenerationController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static String requestInfoJson(String... roleTenantIds) throws Exception {
        Role[] roles = new Role[roleTenantIds.length];
        for (int i = 0; i < roleTenantIds.length; i++) {
            roles[i] = Role.builder().code("SUPERUSER").tenantId(roleTenantIds[i]).build();
        }
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("user-1").roles(java.util.Arrays.asList(roles)).build())
                .build();
        return MAPPER.writeValueAsString(requestInfo);
    }

    private static String requestInfoWithNoRolesJson() throws Exception {
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("user-1").roles(Collections.emptyList()).build())
                .build();
        return MAPPER.writeValueAsString(requestInfo);
    }

    private String dispatchBody(String requestInfoJson, String tenantId) {
        return "{\"RequestInfo\":" + requestInfoJson + ",\"IdDispatchRequest\":{\"tenantId\":\"" + tenantId
                + "\",\"count\":5,\"deviceUuid\":\"device-1\",\"deviceInfo\":\"info\"}}";
    }

    private String searchBody(String requestInfoJson, String tenantId) {
        return "{\"RequestInfo\":" + requestInfoJson + ",\"IdPoolSearch\":{\"ids\":[\"AB-1\"],\"tenantId\":\""
                + tenantId + "\"}}";
    }

    private String updateBody(String requestInfoJson, String... tenantIds) {
        StringBuilder records = new StringBuilder();
        for (int i = 0; i < tenantIds.length; i++) {
            if (i > 0) {
                records.append(",");
            }
            records.append("{\"id\":\"AB-").append(i + 1).append("\",\"tenantId\":\"").append(tenantIds[i]).append("\"}");
        }
        return "{\"RequestInfo\":" + requestInfoJson + ",\"IdRecords\":[" + records + "]}";
    }

    private String generateBody(String requestInfoJson, String... tenantIds) {
        StringBuilder batches = new StringBuilder();
        for (int i = 0; i < tenantIds.length; i++) {
            if (i > 0) {
                batches.append(",");
            }
            batches.append("{\"totalCount\":10,\"tenantId\":\"").append(tenantIds[i]).append("\"}");
        }
        return "{\"RequestInfo\":" + requestInfoJson + ",\"BatchRequestList\":[" + batches + "]}";
    }

    private ResultActions call(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assertDenied(ResultActions result) throws Exception {
        result.andExpect(jsonPath("$.ResponseInfo.status").value("FAILED"))
                .andExpect(jsonPath("$.Errors[0].message").value(DENIED_MESSAGE));
    }

    // ---- _dispatch ----

    @Test
    void dispatchForOtherTenantIsRejected() throws Exception {
        assertDenied(call("/id/id_pool/_dispatch", dispatchBody(requestInfoJson("pb"), "mz")));
        verifyNoInteractions(idDispatchService);
    }

    @Test
    void dispatchWithNoRolesIsAllowedAsInternalCall() throws Exception {
        when(idDispatchService.dispatchIds(any(), any(), any())).thenReturn(IdDispatchResponse.builder().build());
        call("/id/id_pool/_dispatch", dispatchBody(requestInfoWithNoRolesJson(), "mz")).andExpect(status().isOk());
        verify(idDispatchService).dispatchIds(any(), any(), any());
    }

    @Test
    void dispatchForChildTenantIsAllowed() throws Exception {
        when(idDispatchService.dispatchIds(any(), any(), any())).thenReturn(IdDispatchResponse.builder().build());
        call("/id/id_pool/_dispatch", dispatchBody(requestInfoJson("pb"), "pb.amritsar")).andExpect(status().isOk());
        verify(idDispatchService).dispatchIds(any(), any(), any());
    }

    // ---- _search ----

    @Test
    void searchForOtherTenantIsRejected() throws Exception {
        assertDenied(call("/id/id_pool/_search", searchBody(requestInfoJson("pb"), "pbx")));
        verifyNoInteractions(idDispatchService);
    }

    @Test
    void searchWithNoRolesIsAllowedAsInternalCall() throws Exception {
        when(idDispatchService.searchIds(any(), any())).thenReturn(IdDispatchResponse.builder().build());
        call("/id/id_pool/_search", searchBody(requestInfoWithNoRolesJson(), "mz")).andExpect(status().isOk());
        verify(idDispatchService).searchIds(any(), any());
    }

    @Test
    void searchForSameTenantIsAllowed() throws Exception {
        when(idDispatchService.searchIds(any(), any())).thenReturn(IdDispatchResponse.builder().build());
        call("/id/id_pool/_search", searchBody(requestInfoJson("pb"), "pb")).andExpect(status().isOk());
        verify(idDispatchService).searchIds(any(), any());
    }

    // ---- _update ----

    @Test
    void updateWithOneRecordOfOtherTenantIsRejectedAndNothingIsPushed() throws Exception {
        assertDenied(call("/id/id_pool/_update", updateBody(requestInfoJson("pb"), "pb.amritsar", "mz")));
        verifyNoInteractions(producer);
    }

    @Test
    void updateWithNoRolesIsAllowedAsInternalCallAndPushed() throws Exception {
        call("/id/id_pool/_update", updateBody(requestInfoWithNoRolesJson(), "mz"))
                .andExpect(status().isAccepted());
        verify(producer).push(any(), any());
    }

    @Test
    void updateForAllowedTenantsIsAcceptedAndPushed() throws Exception {
        call("/id/id_pool/_update", updateBody(requestInfoJson("pb"), "pb", "pb.amritsar"))
                .andExpect(status().isAccepted());
        verify(producer).push(any(), any());
    }

    // ---- _generate ----

    @Test
    void generateWithOneBatchOfOtherTenantIsRejectedAndNothingIsQueued() throws Exception {
        assertDenied(call("/id/id_pool/_generate", generateBody(requestInfoJson("pb"), "pb.amritsar", "mz")));
        verifyNoInteractions(idGenerationService);
    }

    @Test
    void generateWithNoRolesIsAllowedAsInternalCall() throws Exception {
        when(idGenerationService.generateIDPool(any())).thenReturn(IDPoolGenerationResponse.builder().build());
        call("/id/id_pool/_generate", generateBody(requestInfoWithNoRolesJson(), "mz"))
                .andExpect(status().isOk());
        verify(idGenerationService).generateIDPool(any());
    }

    @Test
    void generateForAllowedTenantsIsAllowed() throws Exception {
        when(idGenerationService.generateIDPool(any())).thenReturn(IDPoolGenerationResponse.builder().build());
        call("/id/id_pool/_generate", generateBody(requestInfoJson("pb"), "pb", "pb.amritsar"))
                .andExpect(status().isOk());
        verify(idGenerationService).generateIDPool(any());
    }
}
