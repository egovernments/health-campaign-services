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
        log.info("Title: {}", request.getTitle());
        log.info("Body: {}", request.getBody());
        log.info("TenantId: {}", request.getTenantId());
        log.info("FacilityId: {}", request.getFacilityId()); // HRUTHVIK: Log facilityId
        log.info("Data: {}", request.getData());
        log.info("Device tokens ({}): {}",
                request.getDeviceTokens() != null ? request.getDeviceTokens().size() : 0,
                request.getDeviceTokens());
        log.info("=================================================");
    }

}
