package org.egov.healthnotification.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.request.RequestInfo;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.egov.healthnotification.web.models.enums.NotificationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class ScheduledNotificationEnrichmentService {

    private final HealthNotificationProperties properties;

    @Autowired
    public ScheduledNotificationEnrichmentService(HealthNotificationProperties properties) {
        this.properties = properties;
    }

    /**
     * Enriches a list of ScheduledNotification objects for create.
     * Updates:
     *   - id → a UUID
     *   - clientReferenceId → a UUID
     *   - status → PENDING
     *   - isDeleted → false
     *   - rowVersion → 1
     *   - attempts → 0
     *   - createdAt → current time
     *   - auditDetails → createdBy, createdTime, lastModifiedBy, lastModifiedTime
     *   - if scheduledAt is not set, default to now (immediate)
     *
     * @param notifications The list of notifications to enrich
     * @param requestInfo   The request info containing user details
     */
    public void enrichForCreate(List<ScheduledNotification> notifications, RequestInfo requestInfo) {
        log.info("Enriching {} scheduled notifications for create", notifications.size());

        String userUuid = extractUserUuid(requestInfo);
        long currentTimeMillis = System.currentTimeMillis();
        ZoneId timezone = ZoneId.of(properties.getNotificationTimezone());
        LocalDate today = LocalDate.now(timezone);

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
            // Only set createdAt if not already set (PostDistributionService sets it to event date)
            if (notification.getCreatedAt() == null) {
                notification.setCreatedAt(today);
            }

            // Set audit details
            notification.setAuditDetails(auditDetails);

            // If scheduledAt is not set, default to today
            if (notification.getScheduledAt() == null) {
                notification.setScheduledAt(today);
                log.debug("scheduledAt not set for notification, defaulting to today");
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
        return Constants.SYSTEM_USER;
    }
}
