package org.egov.healthnotification.repository.rowmapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.egov.healthnotification.web.models.enums.NotificationStatus;
import org.egov.healthnotification.web.models.enums.RecipientType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * Row mapper for the scheduled_notification table.
 * Maps each row of the ResultSet to a ScheduledNotification object.
 */
@Component
@Slf4j
public class ScheduledNotificationRowMapper implements RowMapper<ScheduledNotification> {

    private final ObjectMapper objectMapper;

    @Autowired
    public ScheduledNotificationRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ScheduledNotification mapRow(ResultSet rs, int rowNum) throws SQLException {
        try {
            // Map audit details
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(rs.getString("createdBy"))
                    .lastModifiedBy(rs.getString("lastModifiedBy"))
                    .lastModifiedTime(rs.getLong("lastModifiedTime"))
                    .build();

            // Parse contextData JSON
            Map<String, Object> contextData = null;
            String contextDataStr = rs.getString("contextData");
            if (contextDataStr != null) {
                contextData = objectMapper.readValue(contextDataStr,
                        new TypeReference<Map<String, Object>>() {});
            }

            // Parse status enum
            NotificationStatus status = NotificationStatus.fromValue(rs.getString("status"));

            // Parse recipientType enum
            RecipientType recipientType = RecipientType.fromValue(rs.getString("recipientType"));

            // Handle nullable integer for attempts
            int attempts = rs.getInt("attempts");

            // Handle nullable long for lastAttemptAt
            Long lastAttemptAt = rs.getObject("lastAttemptAt") != null ? rs.getLong("lastAttemptAt") : null;

            return ScheduledNotification.builder()
                    .id(rs.getString("id"))
                    .tenantId(rs.getString("tenantId"))
                    .eventType(rs.getString("eventType"))
                    .entityId(rs.getString("entityId"))
                    .entityType(rs.getString("entityType"))
                    .scheduledAt(rs.getLong("scheduledAt"))
                    .createdAt(rs.getLong("createdAt"))
                    .templateCode(rs.getString("templateCode"))
                    .recipientType(recipientType)
                    .recipientId(rs.getString("recipientId"))
                    .mobileNumber(rs.getString("mobileNumber"))
                    .contextData(contextData)
                    .status(status)
                    .attempts(attempts)
                    .lastAttemptAt(lastAttemptAt)
                    .errorMessage(rs.getString("errorMessage"))
                    .auditDetails(auditDetails)
                    .build();

        } catch (Exception e) {
            log.error("Error mapping scheduled_notification row at rowNum: {}", rowNum, e);
            throw new SQLException("Error mapping scheduled_notification row", e);
        }
    }
}
