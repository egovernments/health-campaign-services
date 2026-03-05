package org.egov.healthnotification.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.request.RequestInfo;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.egov.healthnotification.web.models.enums.NotificationStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Enrichment service for ScheduledNotification entities.
 * Follows the DIGIT Household/Individual pattern:
 *   - enrichForCreate: generates IDs, sets auditDetails, rowVersion, isDeleted, status
 *   - enrichForUpdate: updates auditDetails, increments rowVersion
 *
 * Unlike Household which uses IdGenService for sequential IDs,
 * notifications use UUID since they are internal entities
 * and don't require human-readable IDs.
 */
@Component
@Slf4j
public class ScheduledNotificationEnrichmentService {

    /**
     * Enriches a list of ScheduledNotification objects for creation.
     * Sets:
     *   - id (UUID)
     *   - clientReferenceId (UUID)
     *   - status → PENDING
     *   - isDeleted → false
     *   - rowVersion → 1
     *   - attempts → 0
     *   - createdAt → current time
     *   - auditDetails (createdBy, createdTime, lastModifiedBy, lastModifiedTime)
     *
     * @param notifications The list of notifications to enrich
     * @param requestInfo   The request info containing user details (may be null for Kafka-driven flows)
     */
    public void enrichForCreate(List<ScheduledNotification> notifications, RequestInfo requestInfo) {
        log.info("Enriching {} scheduled notifications for create", notifications.size());

        String userUuid = extractUserUuid(requestInfo);
        long currentTimeMillis = System.currentTimeMillis();

        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(userUuid)
                .createdTime(currentTimeMillis)
                .lastModifiedBy(userUuid)
                .lastModifiedTime(currentTimeMillis)
                .build();

        for (ScheduledNotification notification : notifications) {
            // Generate IDs
            notification.setId(UUID.randomUUID().toString());
            notification.setClientReferenceId(UUID.randomUUID().toString());

            // Set create defaults
            notification.setStatus(NotificationStatus.PENDING);
            notification.setIsDeleted(Boolean.FALSE);
            notification.setRowVersion(1);
            notification.setAttempts(0);
            notification.setCreatedAt(currentTimeMillis);

            // Set audit details
            notification.setAuditDetails(auditDetails);

            // If scheduledAt is not set, default to now (immediate)
            if (notification.getScheduledAt() == null) {
                notification.setScheduledAt(currentTimeMillis);
                log.debug("scheduledAt not set for notification, defaulting to current time");
            }

            log.debug("Enriched notification: id={}, entityId={}, templateCode={}",
                    notification.getId(), notification.getEntityId(), notification.getTemplateCode());
        }

        log.info("Completed enrichment for {} scheduled notifications", notifications.size());
    }

    /**
     * Enriches a list of ScheduledNotification objects for update.
     * Updates:
     *   - auditDetails.lastModifiedBy → current user
     *   - auditDetails.lastModifiedTime → current time
     *   - rowVersion → incremented by 1
     *
     * @param notifications The list of notifications to enrich
     * @param requestInfo   The request info containing user details
     */
    public void enrichForUpdate(List<ScheduledNotification> notifications, RequestInfo requestInfo) {
        log.info("Enriching {} scheduled notifications for update", notifications.size());

        String userUuid = extractUserUuid(requestInfo);
        long currentTimeMillis = System.currentTimeMillis();

        for (ScheduledNotification notification : notifications) {
            // Update audit details
            AuditDetails existingAudit = notification.getAuditDetails();
            if (existingAudit != null) {
                existingAudit.setLastModifiedBy(userUuid);
                existingAudit.setLastModifiedTime(currentTimeMillis);
            } else {
                notification.setAuditDetails(AuditDetails.builder()
                        .lastModifiedBy(userUuid)
                        .lastModifiedTime(currentTimeMillis)
                        .build());
            }

            // Increment row version
            notification.setRowVersion(
                    (notification.getRowVersion() != null ? notification.getRowVersion() : 0) + 1);

            log.debug("Enriched notification for update: id={}, newRowVersion={}",
                    notification.getId(), notification.getRowVersion());
        }

        log.info("Completed update enrichment for {} scheduled notifications", notifications.size());
    }

    /**
     * Extracts user UUID from RequestInfo.
     * Returns "SYSTEM" for Kafka-driven flows where RequestInfo may be null.
     */
    private String extractUserUuid(RequestInfo requestInfo) {
        if (requestInfo != null && requestInfo.getUserInfo() != null
                && requestInfo.getUserInfo().getUuid() != null) {
            return requestInfo.getUserInfo().getUuid();
        }
        return "SYSTEM";
    }
}
