package org.egov.healthnotification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.egov.common.http.client.ServiceRequestClient;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.web.models.MdmsV2Data;
import org.egov.healthnotification.web.models.MdmsV2Response;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MdmsServiceTest {

    @InjectMocks
    private MdmsService mdmsService;

    @Mock
    private ServiceRequestClient serviceRequestClient;

    @Mock
    private HealthNotificationProperties properties;

    private final ObjectMapper mapper = new ObjectMapper();

    private void setupMdmsProperties() {
        when(properties.getMdmsHost()).thenReturn("http://mdms");
        when(properties.getMdmsSearchV2Endpoint()).thenReturn("/mdms-v2/v2/_search");
        when(properties.getNotificationModule()).thenReturn("health-notification");
    }

    @Test
    void fetchNotificationConfigByProjectType_validResponse_returnsConfig() throws Exception {
        setupMdmsProperties();

        ObjectNode dataNode = mapper.createObjectNode();
        dataNode.put("campaignType", "TEST-TYPE-VALID");

        MdmsV2Data config = MdmsV2Data.builder().data(dataNode).build();
        MdmsV2Response response = MdmsV2Response.builder().mdms(List.of(config)).build();

        when(serviceRequestClient.fetchResult(any(), any(), eq(MdmsV2Response.class)))
                .thenReturn(response);

        MdmsV2Data result = mdmsService.fetchNotificationConfigByProjectType(
                "TEST-TYPE-VALID", "mdms-test-tenant1.sub");

        assertNotNull(result);
        assertEquals("TEST-TYPE-VALID", result.getData().get("campaignType").asText());
    }

    @Test
    void fetchNotificationConfigByProjectType_emptyResponse_throwsCustomException() throws Exception {
        setupMdmsProperties();

        MdmsV2Response response = MdmsV2Response.builder().mdms(List.of()).build();

        when(serviceRequestClient.fetchResult(any(), any(), eq(MdmsV2Response.class)))
                .thenReturn(response);

        assertThrows(CustomException.class, () ->
                mdmsService.fetchNotificationConfigByProjectType(
                        "MISSING-TYPE-UNIQUE", "mdms-test-tenant2"));
    }

    @Test
    void fetchNotificationConfigByProjectType_cachedResult_returnsCachedAndSkipsApi() throws Exception {
        setupMdmsProperties();

        ObjectNode dataNode = mapper.createObjectNode();
        dataNode.put("campaignType", "CACHE-TEST-TYPE");

        MdmsV2Data config = MdmsV2Data.builder().data(dataNode).build();
        MdmsV2Response response = MdmsV2Response.builder().mdms(List.of(config)).build();

        when(serviceRequestClient.fetchResult(any(), any(), eq(MdmsV2Response.class)))
                .thenReturn(response);

        // First call fetches from API
        MdmsV2Data first = mdmsService.fetchNotificationConfigByProjectType(
                "CACHE-TEST-TYPE", "mdms-test-tenant3.sub");
        // Second call should use cache
        MdmsV2Data second = mdmsService.fetchNotificationConfigByProjectType(
                "CACHE-TEST-TYPE", "mdms-test-tenant3.sub");

        assertNotNull(first);
        assertNotNull(second);
        assertSame(first, second);
        // API should only be called once
        verify(serviceRequestClient, times(1)).fetchResult(any(), any(), eq(MdmsV2Response.class));
    }

    @Test
    void fetchNotificationConfigByProjectType_apiError_throwsCustomException() throws Exception {
        setupMdmsProperties();

        when(serviceRequestClient.fetchResult(any(), any(), eq(MdmsV2Response.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThrows(CustomException.class, () ->
                mdmsService.fetchNotificationConfigByProjectType(
                        "ERROR-TYPE-UNIQUE", "mdms-test-tenant4"));
    }
}