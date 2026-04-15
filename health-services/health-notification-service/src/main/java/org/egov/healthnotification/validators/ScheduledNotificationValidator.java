package org.egov.healthnotification.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.repository.ScheduledNotificationRepository;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


@Component
@Slf4j
public class ScheduledNotificationValidator {

    private final ScheduledNotificationRepository repository;

    public ScheduledNotificationValidator(ScheduledNotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * Validates a list of scheduled notifications for create.
     * 
     * Phase 1: Required field validation (HARD FAIL)
     *   - Validates that all required fields are present for each notification
     *   - Throws a CustomException with error details if any notification fails
     * 
     * Phase 2: Mobile number check (SOFT SKIP)
     *   - Skips notifications with missing mobile number
     * 
     * Phase 3: Duplicate check (SOFT SKIP)
     *   - Skips duplicate notifications gracefully — supports idempotent Kafka reprocessing
     *   - Throws a CustomException with error details if any notification fails
     * 
     * @param notifications List of scheduled notifications to validate
     */
    public void validateForCreate(List<ScheduledNotification> notifications) {
        log.info("Validating {} scheduled notifications for create", notifications.size());

        // ═══ Phase 1: Required field validation (HARD FAIL) ═══
        Map<String, String> errorMap = new HashMap<>();
        for (int i = 0; i < notifications.size(); i++) {
            validateRequiredFields(notifications.get(i), "notifications[" + i + "]", errorMap);
        }

        if (!errorMap.isEmpty()) {
            log.error("Validation failed with {} error(s): {}", errorMap.size(), errorMap);
            throw new CustomException(errorMap);
        }

        // ═══ Phase 2: Mobile number check (SOFT SKIP) ═══
        // Same behavior as PostDistributionService — skip if no mobile number
        int beforeMobileFilter = notifications.size();
        Iterator<ScheduledNotification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            ScheduledNotification notification = iterator.next();
            if (isBlank(notification.getMobileNumber())) {
                log.info("Skipping notification for recipientId={}: no mobile number available. " +
                                "entityId={}, templateCode={}",
                        notification.getRecipientId(), notification.getEntityId(),
                        notification.getTemplateCode());
                iterator.remove();
            }
        }
        if (notifications.size() < beforeMobileFilter) {
            log.info("Filtered out {} notification(s) with missing mobile number",
                    beforeMobileFilter - notifications.size());
        }

        // ═══ Phase 3: Duplicate check (SOFT SKIP) ═══
        // Skip duplicates gracefully — supports idempotent Kafka reprocessing
        int beforeDupFilter = notifications.size();
        iterator = notifications.iterator();
        while (iterator.hasNext()) {
            ScheduledNotification notification = iterator.next();
            try {
                boolean isDuplicate = repository.isDuplicate(
                        notification.getTenantId(),
                        notification.getEntityType(),
                        notification.getEntityId(),
                        notification.getEventType(),
                        notification.getTemplateCode(),
                        notification.getRecipientId(),
                        notification.getScheduledAt());

                if (isDuplicate) {
                    log.info("Skipping duplicate notification: tenantId={}, entityType={}, entityId={}, eventType={}, " +
                                    "templateCode={}, recipientId={}, scheduledAt={}",
                            notification.getTenantId(), notification.getEntityType(), notification.getEntityId(),
                            notification.getEventType(), notification.getTemplateCode(),
                            notification.getRecipientId(), notification.getScheduledAt());
                    iterator.remove();
                }
            } catch (InvalidTenantIdException e) {
                log.error("Invalid tenant ID during duplicate check: {}. Failing validation.",
                        notification.getTenantId(), e);
                throw new CustomException(Constants.INVALID_TENANT_ID,
                        "Invalid tenant ID during duplicate check: " + notification.getTenantId());
            }
        }
        if (notifications.size() < beforeDupFilter) {
            log.info("Filtered out {} duplicate notification(s)",
                    beforeDupFilter - notifications.size());
        }

        log.info("Validation complete: {} notification(s) passed out of original batch",
                notifications.size());
    }

    /**
     * Validates required fields for a ScheduledNotification.
     * These are hard requirements — if any are missing, it's a programming error.
     */
    private void validateRequiredFields(ScheduledNotification notification,
                                         String prefix,
                                         Map<String, String> errorMap) {
        if (isBlank(notification.getTenantId())) {
            errorMap.put(prefix + ".tenantId", Constants.MSG_TENANT_ID_REQUIRED);
        }

        if (isBlank(notification.getEntityId())) {
            errorMap.put(prefix + ".entityId", Constants.MSG_ENTITY_ID_REQUIRED);
        }

        if (isBlank(notification.getEntityType())) {
            errorMap.put(prefix + ".entityType", Constants.MSG_ENTITY_TYPE_REQUIRED);
        }

        if (isBlank(notification.getEventType())) {
            errorMap.put(prefix + ".eventType", Constants.MSG_EVENT_TYPE_REQUIRED);
        }

        if (isBlank(notification.getTemplateCode())) {
            errorMap.put(prefix + ".templateCode", Constants.MSG_TEMPLATE_CODE_REQUIRED);
        }

        if (isBlank(notification.getRecipientId())) {
            errorMap.put(prefix + ".recipientId", Constants.MSG_RECIPIENT_ID_REQUIRED);
        }

        if (notification.getRecipientType() == null) {
            errorMap.put(prefix + ".recipientType", Constants.MSG_RECIPIENT_TYPE_REQUIRED);
        }

        if (notification.getScheduledAt() == null) {
            errorMap.put(prefix + ".scheduledAt", Constants.MSG_SCHEDULED_AT_REQUIRED);
        }

        if (notification.getContextData() == null) {
            errorMap.put(prefix + ".contextData", Constants.MSG_CONTEXT_DATA_REQUIRED);
        }

        if (notification.getStatus() == null) {
            errorMap.put(prefix + ".status", Constants.MSG_STATUS_REQUIRED);
        }
    }

    /**
     * Utility: checks if a string is null or blank.
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
