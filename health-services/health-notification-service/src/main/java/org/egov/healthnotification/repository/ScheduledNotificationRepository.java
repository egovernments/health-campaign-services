package org.egov.healthnotification.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.producer.Producer;
import org.egov.healthnotification.repository.rowmapper.ScheduledNotificationRowMapper;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

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
     * Fetches pending notifications that are due for sending (scheduledAt <= currentTime).
     * Used by the notification scheduler to pick up the next batch.
     */
    public List<ScheduledNotification> fetchPendingNotifications(String tenantId,
                                                                  Long currentTimeMillis,
                                                                  Integer batchSize) throws InvalidTenantIdException {
        log.info("Fetching pending notifications due before: {}, batchSize: {}", currentTimeMillis, batchSize);

        String query = String.format(
                "SELECT * FROM %s.scheduled_notification WHERE status = :status AND scheduledAt <= :scheduledAt AND isDeleted = false ORDER BY scheduledAt ASC LIMIT :limit",
                SCHEMA_REPLACE_STRING);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("status", "PENDING");
        paramMap.put("scheduledAt", currentTimeMillis);
        paramMap.put("limit", batchSize);

        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        List<ScheduledNotification> results = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);
        log.info("Found {} pending notifications due for sending", results.size());
        return results;
    }

    /**
     * Checks if a duplicate notification already exists (same entity + event + template + recipient).
     * Prevents re-scheduling notifications for the same distribution event.
     */
    public boolean isDuplicate(String tenantId, String entityId, String eventType,
                                String templateCode, String recipientId) throws InvalidTenantIdException {
        log.info("Checking for duplicate: entityId={}, eventType={}, templateCode={}, recipientId={}",
                entityId, eventType, templateCode, recipientId);

        String query = String.format(
                "SELECT * FROM %s.scheduled_notification WHERE entityId = :entityId AND eventType = :eventType AND templateCode = :templateCode AND recipientId = :recipientId AND isDeleted = false LIMIT 1",
                SCHEMA_REPLACE_STRING);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("entityId", entityId);
        paramMap.put("eventType", eventType);
        paramMap.put("templateCode", templateCode);
        paramMap.put("recipientId", recipientId);

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

        if (Boolean.FALSE.equals(includeDeleted)) {
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
