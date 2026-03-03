package org.egov.healthnotification.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.producer.HealthNotificationProducer;
import org.egov.healthnotification.repository.querybuilder.ScheduledNotificationQueryBuilder;
import org.egov.healthnotification.repository.rowmapper.ScheduledNotificationRowMapper;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.egov.healthnotification.web.models.enums.NotificationStatus;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for the scheduled_notification table.
 *
 * WRITE operations: Push to Kafka topic → egov-persister writes to DB.
 * READ operations:  Direct JDBC queries against the DB.
 */
@Repository
@Slf4j
public class ScheduledNotificationRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ScheduledNotificationQueryBuilder queryBuilder;
    private final ScheduledNotificationRowMapper rowMapper;
    private final HealthNotificationProducer producer;
    private final HealthNotificationProperties properties;

    @Autowired
    public ScheduledNotificationRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                            ScheduledNotificationQueryBuilder queryBuilder,
                                            ScheduledNotificationRowMapper rowMapper,
                                            @Qualifier("healthNotificationProducer") HealthNotificationProducer producer,
                                            HealthNotificationProperties properties) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.queryBuilder = queryBuilder;
        this.rowMapper = rowMapper;
        this.producer = producer;
        this.properties = properties;
    }

    // ═══════════════════════════════════════════════════════
    //  WRITE OPERATIONS — Push to Kafka (persister writes to DB)
    // ═══════════════════════════════════════════════════════

    /**
     * Saves a new scheduled notification by pushing to Kafka.
     * The egov-persister service will read from the topic and INSERT into the DB.
     *
     * @param notification The ScheduledNotification to save
     */
    public void save(ScheduledNotification notification) {
        log.info("Pushing scheduled notification to Kafka save topic: id={}, eventType={}, entityId={}",
                notification.getId(), notification.getEventType(), notification.getEntityId());

        producer.push(notification.getTenantId(),
                properties.getScheduledNotificationSaveTopic(),
                Collections.singletonList(notification));

        log.info("Successfully pushed scheduled notification: {} to save topic", notification.getId());
    }

    /**
     * Saves a batch of scheduled notifications by pushing to Kafka.
     *
     * @param notifications The list of ScheduledNotifications to save
     */
    public void saveBatch(List<ScheduledNotification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            log.warn("Empty notification list passed to saveBatch. Skipping.");
            return;
        }

        log.info("Pushing batch of {} scheduled notifications to Kafka save topic", notifications.size());

        // Use tenantId from first notification (all should share the same tenant)
        String tenantId = notifications.get(0).getTenantId();
        producer.push(tenantId,
                properties.getScheduledNotificationSaveTopic(),
                notifications);

        log.info("Successfully pushed batch of {} notifications to save topic", notifications.size());
    }

    /**
     * Updates the status of a scheduled notification by pushing to Kafka.
     * The egov-persister service will read from the topic and UPDATE the DB.
     *
     * @param notification The notification with updated status/attempts
     */
    public void updateStatus(ScheduledNotification notification) {
        log.info("Pushing notification status update to Kafka: id={}, newStatus={}, attempts={}",
                notification.getId(), notification.getStatus(), notification.getAttempts());

        producer.push(notification.getTenantId(),
                properties.getScheduledNotificationUpdateTopic(),
                Collections.singletonList(notification));

        log.info("Successfully pushed status update for notification: {}", notification.getId());
    }

    // ═══════════════════════════════════════════════════════
    //  READ OPERATIONS — Direct JDBC queries against DB
    // ═══════════════════════════════════════════════════════

    /**
     * Searches for scheduled notifications based on filter criteria.
     *
     * @param id Filter by notification ID (optional)
     * @param tenantId Filter by tenant ID (optional)
     * @param status Filter by notification status (optional)
     * @param eventType Filter by event type (optional)
     * @param entityId Filter by entity ID (optional)
     * @param entityType Filter by entity type (optional)
     * @param limit Max results (optional)
     * @param offset Pagination offset (optional)
     * @return List of matching ScheduledNotification objects
     */
    public List<ScheduledNotification> search(String id, String tenantId, NotificationStatus status,
                                               String eventType, String entityId, String entityType,
                                               Integer limit, Integer offset) {
        log.info("Searching scheduled notifications: tenantId={}, status={}, eventType={}",
                tenantId, status, eventType);

        Map<String, Object> paramsMap = new HashMap<>();
        String query = queryBuilder.getSearchQuery(paramsMap, id, tenantId, status,
                eventType, entityId, entityType, limit, offset);

        try {
            List<ScheduledNotification> results = namedParameterJdbcTemplate.query(query, paramsMap, rowMapper);
            log.info("Found {} scheduled notifications matching criteria", results.size());
            return results;
        } catch (Exception e) {
            log.error("Error searching scheduled notifications", e);
            throw new CustomException("NOTIFICATION_SEARCH_ERROR",
                    "Error while searching scheduled notifications");
        }
    }

    /**
     * Fetches pending notifications that are due for sending (scheduledAt <= currentTime).
     * Used by the notification scheduler to pick up the next batch.
     *
     * @param currentTimeMillis Current time in epoch milliseconds
     * @param batchSize Max number of notifications to pick up
     * @return List of pending ScheduledNotification objects
     */
    public List<ScheduledNotification> fetchPendingNotifications(Long currentTimeMillis, Integer batchSize) {
        log.info("Fetching pending notifications due before: {}, batchSize: {}", currentTimeMillis, batchSize);

        Map<String, Object> paramsMap = new HashMap<>();
        String query = queryBuilder.getPendingNotificationsQuery(paramsMap, currentTimeMillis, batchSize);

        try {
            List<ScheduledNotification> results = namedParameterJdbcTemplate.query(query, paramsMap, rowMapper);
            log.info("Found {} pending notifications due for sending", results.size());
            return results;
        } catch (Exception e) {
            log.error("Error fetching pending notifications", e);
            throw new CustomException("NOTIFICATION_FETCH_PENDING_ERROR",
                    "Error while fetching pending notifications");
        }
    }

    /**
     * Checks if a duplicate notification already exists (same entity + event + template + recipient).
     * Prevents re-scheduling notifications for the same distribution event.
     *
     * @param entityId The entity ID
     * @param eventType The event type
     * @param templateCode The template code
     * @param recipientId The recipient ID
     * @return true if a duplicate exists, false otherwise
     */
    public boolean isDuplicate(String entityId, String eventType,
                                String templateCode, String recipientId) {
        log.info("Checking for duplicate: entityId={}, eventType={}, templateCode={}, recipientId={}",
                entityId, eventType, templateCode, recipientId);

        Map<String, Object> paramsMap = new HashMap<>();
        String query = queryBuilder.getDuplicateCheckQuery(paramsMap, entityId, eventType, templateCode, recipientId);

        try {
            List<ScheduledNotification> results = namedParameterJdbcTemplate.query(query, paramsMap, rowMapper);
            boolean isDuplicate = !results.isEmpty();
            log.info("Duplicate check result: {}", isDuplicate);
            return isDuplicate;
        } catch (Exception e) {
            log.error("Error checking for duplicate notification", e);
            return false; // Fail-open: allow the notification if check fails
        }
    }
}
