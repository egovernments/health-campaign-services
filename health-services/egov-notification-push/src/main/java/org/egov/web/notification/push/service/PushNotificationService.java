package org.egov.web.notification.push.service;

import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;

public interface PushNotificationService {

    SendResult sendPushNotification(PushNotificationRequest request, int attempt);

    default SendResult sendPushNotification(PushNotificationRequest request) {
        return sendPushNotification(request, 0);
    }

}
