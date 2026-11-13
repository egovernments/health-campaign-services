package org.egov.web.notification.push.service;

import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class ConsolePushService implements PushNotificationService {

    @Override
    public void sendPushNotification(PushNotificationRequest request) {
        log.info("=== CONSOLE PUSH NOTIFICATION (FCM disabled) ===");
        log.info("Title length: {}", request.getTitle() != null ? request.getTitle().length() : 0);
        log.info("Body length: {}", request.getBody() != null ? request.getBody().length() : 0);
        log.info("TenantId: {}", request.getTenantId());
        log.info("FacilityId: {}", request.getFacilityId()); // Log facilityId
        log.info("Data keys: {}", request.getData() != null ? request.getData().keySet() : null);
        log.info("Device tokens count: {}",
                request.getDeviceTokens() != null ? request.getDeviceTokens().size() : 0);
        log.info("=================================================");
    }

}
