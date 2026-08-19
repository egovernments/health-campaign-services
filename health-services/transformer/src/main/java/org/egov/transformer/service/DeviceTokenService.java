package org.egov.transformer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.facility.*;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.models.devicetoken.DeviceToken;
import org.egov.transformer.models.devicetoken.DeviceTokenSearchRequest;
import org.egov.transformer.producer.TransformerErrorProducer;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class DeviceTokenService {

    private final TransformerProperties properties;
    private final ServiceRequestClient serviceRequestClient;
    private final ObjectMapper objectMapper;
    private final TransformerErrorProducer errorProducer;

    public DeviceTokenService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient, ObjectMapper objectMapper, TransformerErrorProducer errorProducer) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.objectMapper = objectMapper;
        this.errorProducer = errorProducer;
    }

    public DeviceToken searchDeviceToken(String userId, String tenantId) {
        DeviceTokenSearchRequest deviceTokenSearchRequest = DeviceTokenSearchRequest.builder()
                .userIds(Collections.singletonList(userId))
                .tenantId(tenantId)
                .requestInfo(RequestInfo.builder()
                        .userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build()).build();
        try {
            JsonNode response = serviceRequestClient.fetchResult(
                    new StringBuilder(properties.getDeviceTokenHost()
                            + properties.getDeviceTokenSearchUrl()
                            + "?limit=1"
                            + "&offset=0&tenantId=" + tenantId),
                    deviceTokenSearchRequest,
                    JsonNode.class);
            List<DeviceToken> deviceTokens = objectMapper.convertValue(response.get("deviceTokens"), List.class);
            return deviceTokens.get(0);
        } catch (Exception e) {
            log.error("Error while fetching Device Token {}", ExceptionUtils.getStackTrace(e));
            errorProducer.sendToErrorTopic(deviceTokenSearchRequest, null, e);
            return null;
        }
    }

}
