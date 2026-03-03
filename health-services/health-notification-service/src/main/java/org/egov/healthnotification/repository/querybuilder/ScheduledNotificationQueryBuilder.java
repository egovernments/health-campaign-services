package org.egov.healthnotification.repository.querybuilder;

import org.egov.healthnotification.web.models.enums.NotificationStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Query builder for the scheduled_notification table.
 * Builds parameterized SQL queries for CRUD operations.
 */
@Component
public class ScheduledNotificationQueryBuilder {

    private static final String INSERT_QUERY = "INSERT INTO scheduled_notification " +
            "(id, tenantId, eventType, entityId, entityType, scheduledAt, createdAt, " +
            "templateCode, recipientType, recipientId, mobileNumber, contextData, " +
            "status, attempts, lastAttemptAt, errorMessage, createdBy, lastModifiedBy, lastModifiedTime) " +
            "VALUES (:id, :tenantId, :eventType, :entityId, :entityType, :scheduledAt, :createdAt, " +
            ":templateCode, :recipientType, :recipientId, :mobileNumber, :contextData::jsonb, " +
            ":status, :attempts, :lastAttemptAt, :errorMessage, :createdBy, :lastModifiedBy, :lastModifiedTime)";

    private static final String BASE_SEARCH_QUERY = "SELECT * FROM scheduled_notification ";

    private static final String UPDATE_STATUS_QUERY = "UPDATE scheduled_notification " +
            "SET status = :status, attempts = :attempts, lastAttemptAt = :lastAttemptAt, " +
            "errorMessage = :errorMessage, lastModifiedBy = :lastModifiedBy, lastModifiedTime = :lastModifiedTime " +
            "WHERE id = :id";

    /**
     * Returns the INSERT query for a new scheduled notification.
     */
    public String getInsertQuery() {
        return INSERT_QUERY;
    }

    /**
     * Returns the UPDATE query for changing status/attempts.
     */
    public String getUpdateStatusQuery() {
        return UPDATE_STATUS_QUERY;
    }

    /**
     * Builds a dynamic search query based on the provided filter criteria.
     * Adds WHERE clauses for each non-null parameter.
     *
     * @param paramsMap Map to populate with named parameters
     * @param id Filter by notification ID
     * @param tenantId Filter by tenant ID
     * @param status Filter by notification status
     * @param eventType Filter by event type
     * @param entityId Filter by entity ID
     * @param entityType Filter by entity type
     * @param limit Max results
     * @param offset Pagination offset
     * @return The constructed SQL query string
     */
    public String getSearchQuery(Map<String, Object> paramsMap,
                                  String id, String tenantId, NotificationStatus status,
                                  String eventType, String entityId, String entityType,
                                  Integer limit, Integer offset) {
        StringBuilder query = new StringBuilder(BASE_SEARCH_QUERY);
        boolean firstCondition = true;

        if (id != null) {
            addClause(query, firstCondition, "id = :id");
            paramsMap.put("id", id);
            firstCondition = false;
        }

        if (tenantId != null) {
            addClause(query, firstCondition, "tenantId = :tenantId");
            paramsMap.put("tenantId", tenantId);
            firstCondition = false;
        }

        if (status != null) {
            addClause(query, firstCondition, "status = :status");
            paramsMap.put("status", status.toString());
            firstCondition = false;
        }

        if (eventType != null) {
            addClause(query, firstCondition, "eventType = :eventType");
            paramsMap.put("eventType", eventType);
            firstCondition = false;
        }

        if (entityId != null) {
            addClause(query, firstCondition, "entityId = :entityId");
            paramsMap.put("entityId", entityId);
            firstCondition = false;
        }

        if (entityType != null) {
            addClause(query, firstCondition, "entityType = :entityType");
            paramsMap.put("entityType", entityType);
            firstCondition = false;
        }

        query.append(" ORDER BY scheduledAt ASC");

        if (limit != null) {
            query.append(" LIMIT :limit");
            paramsMap.put("limit", limit);
        }

        if (offset != null) {
            query.append(" OFFSET :offset");
            paramsMap.put("offset", offset);
        }

        return query.toString();
    }

    /**
     * Builds a query to fetch PENDING notifications whose scheduledAt time has passed.
     * Used by the notification scheduler to pick up notifications that are due.
     *
     * @param paramsMap Map to populate with named parameters
     * @param currentTimeMillis Current time in epoch milliseconds
     * @param limit Max number of notifications to pick up in one batch
     * @return The constructed SQL query string
     */
    public String getPendingNotificationsQuery(Map<String, Object> paramsMap,
                                                Long currentTimeMillis, Integer limit) {
        String query = BASE_SEARCH_QUERY +
                "WHERE status = :status AND scheduledAt <= :currentTime " +
                "ORDER BY scheduledAt ASC LIMIT :limit";

        paramsMap.put("status", NotificationStatus.PENDING.toString());
        paramsMap.put("currentTime", currentTimeMillis);
        paramsMap.put("limit", limit);

        return query;
    }

    /**
     * Builds a query to check for duplicate notifications (same entity + event + template + recipient).
     *
     * @param paramsMap Map to populate with named parameters
     * @param entityId The entity ID
     * @param eventType The event type
     * @param templateCode The template code
     * @param recipientId The recipient ID
     * @return The constructed SQL query string
     */
    public String getDuplicateCheckQuery(Map<String, Object> paramsMap,
                                          String entityId, String eventType,
                                          String templateCode, String recipientId) {
        String query = BASE_SEARCH_QUERY +
                "WHERE entityId = :entityId AND eventType = :eventType " +
                "AND templateCode = :templateCode AND recipientId = :recipientId " +
                "AND status IN (:activeStatuses)";

        paramsMap.put("entityId", entityId);
        paramsMap.put("eventType", eventType);
        paramsMap.put("templateCode", templateCode);
        paramsMap.put("recipientId", recipientId);
        paramsMap.put("activeStatuses", List.of(
                NotificationStatus.PENDING.toString(),
                NotificationStatus.SENT.toString()));

        return query;
    }

    private void addClause(StringBuilder query, boolean isFirstCondition, String clause) {
        if (isFirstCondition) {
            query.append(" WHERE ").append(clause);
        } else {
            query.append(" AND ").append(clause);
        }
    }
}
