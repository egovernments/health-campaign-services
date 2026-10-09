package org.egov.web.notification.push.consumer;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.egov.web.notification.push.service.DeviceTokenService;
import org.egov.web.notification.push.service.PushNotificationService;
import org.egov.web.notification.push.web.contract.DeviceToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PushNotificationListener {

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceTokenService deviceTokenService;

    @KafkaListener(topicPattern = "(${kafka.tenant.id.pattern}){0,1}${kafka.topics.notification.push}")
    public void processPushNotification(HashMap<String, Object> consumerRecord) {
        try {
            PushNotificationRequest request = objectMapper.convertValue(consumerRecord, PushNotificationRequest.class);
            log.info("Received push notification request for tenant: {}", request.getTenantId());

            // If facilityId is present and deviceTokens are absent,
            // resolve device tokens from DB using facilityId and recipientRoles
            if ((request.getDeviceTokens() == null || request.getDeviceTokens().isEmpty())
                    && request.getFacilityId() != null && !request.getFacilityId().isEmpty()) {

                String facilityId = request.getFacilityId();
                String tenantId = request.getTenantId();
                List<String> roles = request.getRecipientRoles();

                List<DeviceToken> tokens;
                if (roles != null && !roles.isEmpty()) {
                    log.info("Resolving device tokens for facilityId: {} with roles: {}", facilityId, roles);
                    tokens = deviceTokenService.getTokensByFacilityIdAndRoles(facilityId, roles, tenantId);
                } else {
                    log.info("No recipientRoles in request. Resolving all tokens for facilityId: {}", facilityId);
                    tokens = deviceTokenService.getTokensByFacilityId(facilityId, tenantId);
                }

                if (tokens == null || tokens.isEmpty()) {
                    log.warn("No device tokens found for facilityId: {}, roles: {}. Skipping push notification.",
                            facilityId, roles);
                    return;
                }

                List<String> resolvedTokens = tokens.stream()
                        .map(DeviceToken::getDeviceToken)
                        .collect(Collectors.toList());
                request.setDeviceTokens(resolvedTokens);
                log.info("Resolved {} device token(s) for facilityId: {}, roles: {}",
                        resolvedTokens.size(), facilityId, roles);
            }

            pushNotificationService.sendPushNotification(request);
        } catch (Exception e) {
            log.error("Error processing push notification message: ", e);
        }
    }

}
