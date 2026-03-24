package org.egov.web.notification.push.service;

import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;

public interface PushNotificationService {

    void sendPushNotification(PushNotificationRequest request);

}
