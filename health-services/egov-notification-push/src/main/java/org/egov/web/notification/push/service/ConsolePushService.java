package org.egov.web.notification.push.service;

import java.util.List;

import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@ConditionalOnProperty(name = "fcm.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class ConsolePushService implements PushNotificationService {

    @Override
    public SendResult sendPushNotification(PushNotificationRequest request, int attempt) {
        log.info("=== CONSOLE PUSH NOTIFICATION (FCM disabled) ===");
        log.info("Title: {}", request.getTitle());
        log.info("Body: {}", request.getBody());
        log.info("TenantId: {}", request.getTenantId());
        log.info("FacilityId: {}", request.getFacilityId()); // Log facilityId
        log.info("Data: {}", request.getData());
        int count = request.getDeviceTokens() != null ? request.getDeviceTokens().size() : 0;
        log.info("Device tokens ({}): {}", count, count == 0 ? List.of()
                : request.getDeviceTokens().stream().map(DeviceTokenService::tokenSuffix).toList());
        log.info("=================================================");
        return new SendResult(count, count, 0, 0);
    }

}
