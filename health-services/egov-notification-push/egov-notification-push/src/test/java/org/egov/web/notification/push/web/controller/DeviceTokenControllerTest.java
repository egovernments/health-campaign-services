package org.egov.web.notification.push.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.egov.web.notification.push.service.DeviceTokenService;
import org.egov.web.notification.push.utils.ResponseInfoFactory;
import org.egov.web.notification.push.web.contract.DeviceToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeviceTokenController.class)
@Import(RestTemplateAutoConfiguration.class)
class DeviceTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceTokenService deviceTokenService;

    @MockBean
    private ResponseInfoFactory responseInfoFactory;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void registerDeviceToken_validRequest_returns200() throws Exception {
        DeviceToken token = DeviceToken.builder()
                .id("gen-id")
                .deviceToken("fcm-tok-1")
                .deviceType("ANDROID")
                .userId("user-1")
                .tenantId("tenant1")
                .build();

        when(deviceTokenService.registerDeviceTokens(any(), any())).thenReturn(List.of(token));

        String requestBody = """
                {
                    "RequestInfo": {
                        "apiId": "api",
                        "ver": "1.0",
                        "userInfo": {
                            "uuid": "user-1"
                        }
                    },
                    "deviceTokens": [
                        {
                            "deviceToken": "fcm-tok-1",
                            "deviceType": "ANDROID",
                            "tenantId": "tenant1"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/device-token/v1/_register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceTokens[0].deviceToken").value("fcm-tok-1"));
    }

    @Test
    void deleteDeviceToken_validRequest_returns200() throws Exception {
        String requestBody = """
                {
                    "RequestInfo": {
                        "apiId": "api",
                        "ver": "1.0",
                        "userInfo": {
                            "uuid": "user-1"
                        }
                    },
                    "deviceTokens": [
                        {
                            "deviceToken": "fcm-tok-to-delete",
                            "deviceType": "ANDROID",
                            "userId": "user-1"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/device-token/v1/_delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());
    }
}