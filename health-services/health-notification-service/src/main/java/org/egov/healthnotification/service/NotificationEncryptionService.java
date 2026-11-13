package org.egov.healthnotification.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.healthnotification.util.EncryptionDecryptionUtil;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NotificationEncryptionService {
    private final EncryptionDecryptionUtil encryptionDecryptionUtil;

    public NotificationEncryptionService(EncryptionDecryptionUtil encryptionDecryptionUtil) {
        this.encryptionDecryptionUtil = encryptionDecryptionUtil;
    }

    public List<ScheduledNotification> encrypt(List<ScheduledNotification> notifications, String key) {
        List<ScheduledNotification> encryptedNotifications = encryptionDecryptionUtil
                .encryptObject(notifications, key);
        return encryptedNotifications;
    }

    public List<ScheduledNotification> decrypt(List<ScheduledNotification> notifications, String key, RequestInfo requestInfo) {
        List<ScheduledNotification> encryptedNotifications = filterEncryptedNotifications(notifications);
        List<ScheduledNotification> decryptedNotifications = encryptionDecryptionUtil
                .decryptObject(encryptedNotifications, key, requestInfo);

        if (notifications.size() > decryptedNotifications.size()) {
            // add the already decrypted objects to the list
            List<String> ids = decryptedNotifications.stream()
                    .map(ScheduledNotification::getId)
                    .collect(Collectors.toList());
            for (ScheduledNotification notification : notifications) {
                if (!ids.contains(notification.getId())) {
                    decryptedNotifications.add(notification);
                }
            }
        }
        return decryptedNotifications;
    }

    private List<ScheduledNotification> filterEncryptedNotifications(List<ScheduledNotification> notifications) {
        return notifications.stream()
                .filter(notification -> isCipherText(notification.getMobileNumber())
                        || (notification.getContextData() != null && isCipherText(notification.getContextData().toString())))
                .collect(Collectors.toList());
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
