package org.egov.healthnotification.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.healthnotification.util.EncryptionDecryptionUtil;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class NotificationEncryptionService {
    private final EncryptionDecryptionUtil encryptionDecryptionUtil;
    private final NotificationDispatchService notificationDispatchService;

    public NotificationEncryptionService(EncryptionDecryptionUtil encryptionDecryptionUtil,
                                         NotificationDispatchService notificationDispatchService) {
        this.encryptionDecryptionUtil = encryptionDecryptionUtil;
        this.notificationDispatchService = notificationDispatchService;
    }

    public List<ScheduledNotification> encrypt(List<ScheduledNotification> notifications, String key) {
        List<ScheduledNotification> encryptedNotifications = encryptionDecryptionUtil
                .encryptObject(notifications, key);
        return encryptedNotifications;
    }

    public List<ScheduledNotification> decrypt(List<ScheduledNotification> notifications, String key, RequestInfo requestInfo) {
        if (notifications == null || notifications.isEmpty()) {
            return notifications;
        }

        List<ScheduledNotification> decryptedNotifications = new ArrayList<>();
        for (ScheduledNotification notification : notifications) {
            if (!isEncrypted(notification)) {
                decryptedNotifications.add(notification);
                continue;
            }

            try {
                decryptedNotifications.addAll(encryptionDecryptionUtil.decryptObject(
                        Collections.singletonList(notification), key, requestInfo));
            } catch (Exception exception) {
                String errorMessage = String.format("Failed to decrypt notification id=%s: %s",
                        notification.getId(), exception.getMessage());
                log.error(errorMessage, exception);
                notificationDispatchService.markFailed(notification, errorMessage);
            }
        }

        return decryptedNotifications;
    }

    private boolean isEncrypted(ScheduledNotification notification) {
        return isCipherText(notification.getMobileNumber())
                || (notification.getContextData() != null && isCipherText(notification.getContextData().toString()));
    }

    private boolean isCipherText(String text) {
        //sample encrypted data - 640326|7hsFfY6olwUbet1HdcLxbStR1BSkOye8N3M=
        //Encrypted data will have a prefix followed by '|' and the base64 encoded data
        if ((StringUtils.isNotBlank(text) && text.contains("|"))) {
            String base64Data = text.split("\\|")[1];
            return StringUtils.isNotBlank(base64Data) && (base64Data.length() % 4 == 0 || base64Data.endsWith("="));
        }
        return false;
    }
}
