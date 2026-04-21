package org.egov.healthnotification.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.producer.Producer;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.repository.rowmapper.ScheduledNotificationRowMapper;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.egov.common.utils.CommonUtils.constructTotalCountCTEAndReturnResult;
import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

/**
 * Repository for scheduled_notification table.
 * Extends GenericRepository to follow the DIGIT Household/Individual pattern:
 *   - WRITE ops → save(objects, topic) → producer.push(tenantId, topic, value) → Kafka → persister
 *   - READ ops  → NamedParameterJdbcTemplate with :namedParams
 *
 * NOTE: Redis caching is intentionally disabled (no-op).
 * Scheduled notifications are write-once, process-once entities — they don't benefit
 * from caching (unlike Household/Individual which serve high-frequency lookups).
 */
@Repository
@Slf4j
public class ScheduledNotificationRepository extends GenericRepository<ScheduledNotification> {

    @Autowired
    protected ScheduledNotificationRepository(Producer producer,
                                               NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                               RedisTemplate<String, Object> redisTemplate,
                                               SelectQueryBuilder selectQueryBuilder,
                                               ScheduledNotificationRowMapper rowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                rowMapper, Optional.of("scheduled_notification"));
    }

    // ═══════════════════════════════════════════════════════
    //  Redis caching — intentionally disabled (no-op)
    //
    //  Scheduled notifications are fire-and-forget entities:
    //  Created → picked up by scheduler → sent/failed.
    //  No repeated lookups → no caching benefit.
    // ═══════════════════════════════════════════════════════

    @Override
    public void putInCache(List<ScheduledNotification> objects) {
        // No-op: Scheduled notifications don't benefit from caching
    }

    @Override
    public void putInCache(List<ScheduledNotification> objects, String key) {
        // No-op: Scheduled notifications don't benefit from caching
    }

    // ═══════════════════════════════════════════════════════
    //  WRITE OPERATIONS — Inherited from GenericRepository
    //
    //  save(List<ScheduledNotification> objects, String topic)
    //    → producer.push(tenantId, topic, objects)
    //    → putInCache(objects)  [no-op, overridden above]
    //
    //  Usage:
    //    repository.save(notifications, config.getScheduledNotificationSaveTopic());
    // ═══════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════
    //  READ OPERATIONS — Direct DB queries (no cache)
    // ═══════════════════════════════════════════════════════

    /**
     * Fetches scheduled notifications by ID.
     * Goes directly to DB (no cache lookup — notifications are write-once entities).
     */
    public SearchResponse<ScheduledNotification> findById(String tenantId, List<String> ids,
                                                           String columnName, Boolean includeDeleted) throws InvalidTenantIdException {
        String query = String.format("SELECT * FROM %s.scheduled_notification WHERE %s IN (:ids) AND isDeleted = false",
                SCHEMA_REPLACE_STRING, columnName);
        if (null != includeDeleted && includeDeleted) {
            query = String.format("SELECT * FROM %s.scheduled_notification WHERE %s IN (:ids)",
                    SCHEMA_REPLACE_STRING, columnName);
        }

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramMap, this.namedParameterJdbcTemplate);

        List<ScheduledNotification> results = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);
        return SearchResponse.<ScheduledNotification>builder()
                .totalCount(totalCount)
                .response(results)
                .build();
    }

    /**
     * Fetches pending notifications scheduled for the given date.
     * Used by the notification scheduler (runs daily) to pick up the day's batch.
     */
    public List<ScheduledNotification> fetchPendingNotifications(String tenantId,
                                                                  LocalDate scheduledDate,
                                                                  Integer batchSize) throws InvalidTenantIdException {
        log.info("Fetching pending notifications for date: {}, batchSize: {}", scheduledDate, batchSize);

        String query = String.format(
                "SELECT * FROM %s.scheduled_notification WHERE tenantId = :tenantId AND status = :status AND scheduledAt <= :scheduledAt AND isDeleted = false ORDER BY scheduledAt ASC LIMIT :limit",
                SCHEMA_REPLACE_STRING);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("tenantId", tenantId);
        paramMap.put("status", "PENDING");
        paramMap.put("scheduledAt", Date.valueOf(scheduledDate));
        paramMap.put("limit", batchSize);

        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        List<ScheduledNotification> results = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);
        log.info("Found {} pending notifications for date: {}", results.size(), scheduledDate);
        return results;
    }

    /**
     * Atomically claims pending notifications by flipping them to IN_PROGRESS in the same statement
     * that selects them. This prevents two scheduler instances from picking the same rows.
     *
     * Timed-out IN_PROGRESS rows are also reclaimable so a crashed scheduler instance does not
     * leave notifications stuck forever.
     */
    public List<ScheduledNotification> claimPendingNotifications(String tenantId,
                                                                 LocalDate scheduledDate,
                                                                 Integer batchSize,
                                                                 Long staleInProgressCutoffTime) throws InvalidTenantIdException {
        log.info("Claiming pending notifications for date: {}, batchSize: {}", scheduledDate, batchSize);

        String query = String.format(
                "WITH claimed AS (" +
                        "    SELECT id FROM %s.scheduled_notification " +
                        "    WHERE tenantId = :tenantId AND scheduledAt <= :scheduledAt AND isDeleted = false " +
                        "      AND (" +
                        "          status = :pendingStatus " +
                        "          OR (status = :inProgressStatus AND COALESCE(lastModifiedTime, 0) < :staleInProgressCutoffTime)" +
                        "      ) " +
                        "    ORDER BY CASE WHEN status = :pendingStatus THEN 0 ELSE 1 END, scheduledAt ASC, id ASC " +
                        "    LIMIT :limit " +
                        "    FOR UPDATE SKIP LOCKED" +
                        ") " +
                        "UPDATE %s.scheduled_notification sn " +
                        "SET status = :inProgressStatus, " +
                        "    rowVersion = COALESCE(sn.rowVersion, 0) + 1, " +
                        "    lastModifiedBy = :lastModifiedBy, " +
                        "    lastModifiedTime = :lastModifiedTime " +
                        "FROM claimed " +
                        "WHERE sn.id = claimed.id AND sn.tenantId = :tenantId " +
                        "RETURNING sn.*",
                SCHEMA_REPLACE_STRING, SCHEMA_REPLACE_STRING);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("tenantId", tenantId);
        paramMap.put("pendingStatus", "PENDING");
        paramMap.put("inProgressStatus", "IN_PROGRESS");
        paramMap.put("scheduledAt", Date.valueOf(scheduledDate));
        paramMap.put("limit", batchSize);
        paramMap.put("staleInProgressCutoffTime", staleInProgressCutoffTime);
        paramMap.put("lastModifiedBy", Constants.SYSTEM_USER);
        paramMap.put("lastModifiedTime", System.currentTimeMillis());

        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        List<ScheduledNotification> results = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);
        log.info("Claimed {} pending notifications for date: {}", results.size(), scheduledDate);
        return results;
    }

    /**
     * Checks if a duplicate notification already exists (same entity + event + template + recipient).
     * Prevents re-scheduling notifications for the same distribution event.
     */
    public boolean isDuplicate(String tenantId, String entityType, String entityId, String eventType,
                                String templateCode, String recipientId, LocalDate scheduledAt)
            throws InvalidTenantIdException {
        log.info("Checking for duplicate: tenantId={}, entityType={}, entityId={}, eventType={}, templateCode={}, recipientId={}, scheduledAt={}",
                tenantId, entityType, entityId, eventType, templateCode, recipientId, scheduledAt);

        String query = String.format(
                "SELECT * FROM %s.scheduled_notification " +
                        "WHERE tenantId = :tenantId AND entityType = :entityType AND entityId = :entityId " +
                        "AND eventType = :eventType AND templateCode = :templateCode " +
                        "AND recipientId = :recipientId AND scheduledAt = :scheduledAt AND isDeleted = false LIMIT 1",
                SCHEMA_REPLACE_STRING);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("tenantId", tenantId);
        paramMap.put("entityType", entityType);
        paramMap.put("entityId", entityId);
        paramMap.put("eventType", eventType);
        paramMap.put("templateCode", templateCode);
        paramMap.put("recipientId", recipientId);
        paramMap.put("scheduledAt", Date.valueOf(scheduledAt));

        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        List<ScheduledNotification> results = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);

        boolean isDuplicate = results != null && !results.isEmpty();
        log.info("Duplicate check result: {}", isDuplicate);
        return isDuplicate;
    }

    /**
     * Search for notifications based on filter criteria.
     */
    public SearchResponse<ScheduledNotification> search(String tenantId, String status,
                                                         String eventType, String entityId,
                                                         Integer limit, Integer offset,
                                                         Boolean includeDeleted) throws InvalidTenantIdException {
        log.info("Searching scheduled notifications: tenantId={}, status={}, eventType={}", tenantId, status, eventType);

        StringBuilder queryBuilder = new StringBuilder(
                String.format("SELECT * FROM %s.scheduled_notification", SCHEMA_REPLACE_STRING));
        Map<String, Object> paramsMap = new HashMap<>();
        boolean hasCondition = false;

        if (tenantId != null) {
            queryBuilder.append(hasCondition ? " AND " : " WHERE ");
            queryBuilder.append("tenantId = :tenantId");
            paramsMap.put("tenantId", tenantId);
            hasCondition = true;
        }

        if (status != null) {
            queryBuilder.append(hasCondition ? " AND " : " WHERE ");
            queryBuilder.append("status = :status");
            paramsMap.put("status", status);
            hasCondition = true;
        }

        if (eventType != null) {
            queryBuilder.append(hasCondition ? " AND " : " WHERE ");
            queryBuilder.append("eventType = :eventType");
            paramsMap.put("eventType", eventType);
            hasCondition = true;
        }

        if (entityId != null) {
            queryBuilder.append(hasCondition ? " AND " : " WHERE ");
            queryBuilder.append("entityId = :entityId");
            paramsMap.put("entityId", entityId);
            hasCondition = true;
        }

        if (!Boolean.TRUE.equals(includeDeleted)) {
            queryBuilder.append(hasCondition ? " AND " : " WHERE ");
            queryBuilder.append("isDeleted = :isDeleted");
            paramsMap.put("isDeleted", false);
            hasCondition = true;
        }

        queryBuilder.append(" ORDER BY scheduledAt ASC");

        String finalQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(queryBuilder.toString(), tenantId);
        Long totalCount = constructTotalCountCTEAndReturnResult(finalQuery, paramsMap, this.namedParameterJdbcTemplate);

        queryBuilder.append(" LIMIT :limit OFFSET :offset");
        paramsMap.put("limit", limit != null ? limit : 100);
        paramsMap.put("offset", offset != null ? offset : 0);

        finalQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(queryBuilder.toString(), tenantId);
        List<ScheduledNotification> results = this.namedParameterJdbcTemplate.query(finalQuery, paramsMap, this.rowMapper);
        log.info("Found {} scheduled notifications matching criteria", results.size());

        return SearchResponse.<ScheduledNotification>builder()
                .totalCount(totalCount)
                .response(results)
                .build();
    }
}
