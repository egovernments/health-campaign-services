package org.egov.web.notification.push.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.egov.web.notification.push.service.PushNotificationApiService;
import org.egov.web.notification.push.utils.ResponseInfoFactory;
import org.egov.web.notification.push.web.contract.PushNotificationApiRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PushNotificationController.class)
@Import(RestTemplateAutoConfiguration.class)
class PushNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PushNotificationApiService pushNotificationApiService;

    @MockBean
    private ResponseInfoFactory responseInfoFactory;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void sendPushNotification_validRequest_returns200() throws Exception {
        when(pushNotificationApiService.sendNotification(any(PushNotificationApiRequest.class)))
                .thenReturn(3);

        String requestBody = """
                {
                    "RequestInfo": {
                        "apiId": "api",
                        "ver": "1.0",
                        "msgId": "msg-1"
                    },
                    "title": "Stock Issue",
                    "body": "50 ITN Nets issued",
                    "data": {
                        "notificationType": "STOCK",
                        "eventType": "STOCK_ISSUE_PUSH_NOTIFICATION"
                    },
                    "deviceTokens": ["token1", "token2", "token3"],
                    "tenantId": "tenant1"
                }
                """;

        mockMvc.perform(post("/push/v1/_send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Push notification sent to 3 devices"));
    }

    @Test
    void sendPushNotification_withUserUuids_returns200() throws Exception {
        when(pushNotificationApiService.sendNotification(any(PushNotificationApiRequest.class)))
                .thenReturn(2);

        String requestBody = """
                {
                    "RequestInfo": {
                        "apiId": "api",
                        "ver": "1.0"
                    },
                    "title": "Referral",
                    "body": "New referral received",
                    "data": {
                        "notificationType": "REFERRAL"
                    },
                    "userUuids": ["uuid-1", "uuid-2"],
                    "tenantId": "tenant1"
                }
                """;

        mockMvc.perform(post("/push/v1/_send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Push notification sent to 2 devices"));
    }
}
