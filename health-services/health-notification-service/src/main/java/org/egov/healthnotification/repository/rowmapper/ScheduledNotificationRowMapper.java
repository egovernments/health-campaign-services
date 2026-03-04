package org.egov.healthnotification.repository.rowmapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.egov.healthnotification.web.models.enums.NotificationStatus;
import org.egov.healthnotification.web.models.enums.RecipientType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Row mapper for scheduled_notification table.
 * Follows DIGIT Household/Individual pattern: implements RowMapper<T> with mapRow().
 */
@Component
@Slf4j
public class ScheduledNotificationRowMapper implements RowMapper<ScheduledNotification> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ScheduledNotification mapRow(ResultSet rs, int rowNum) throws SQLException {

        // Parse contextData JSON
        Map<String, Object> contextData = new HashMap<>();
        String contextDataStr = rs.getString("contextData");
        if (!ObjectUtils.isEmpty(contextDataStr)) {
            try {
                contextData = objectMapper.readValue(contextDataStr,
                        new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.error("Error parsing contextData JSON for notification: {}", rs.getString("id"), e);
            }
        }

        // Build audit details (matching Household pattern)
        AuditDetails auditDetails = AuditDetails.builder()
                .createdBy(rs.getString("createdBy"))
                .createdTime(rs.getLong("createdTime"))
                .lastModifiedBy(rs.getString("lastModifiedBy"))
                .lastModifiedTime(rs.getLong("lastModifiedTime"))
                .build();

        // Handle nullable Long fields
        Long lastAttemptAt = rs.getLong("lastAttemptAt");
        if (rs.wasNull()) {
            lastAttemptAt = null;
        }

        return ScheduledNotification.builder()
                .id(rs.getString("id"))
                .clientReferenceId(rs.getString("clientReferenceId"))
                .tenantId(rs.getString("tenantId"))
                .eventType(rs.getString("eventType"))
                .entityId(rs.getString("entityId"))
                .entityType(rs.getString("entityType"))
                .scheduledAt(rs.getLong("scheduledAt"))
                .createdAt(rs.getLong("createdAt"))
                .templateCode(rs.getString("templateCode"))
                .recipientType(RecipientType.fromValue(rs.getString("recipientType")))
                .recipientId(rs.getString("recipientId"))
                .mobileNumber(rs.getString("mobileNumber"))
                .contextData(contextData)
                .status(NotificationStatus.fromValue(rs.getString("status")))
                .attempts(rs.getInt("attempts"))
                .lastAttemptAt(lastAttemptAt)
                .errorMessage(rs.getString("errorMessage"))
                .isDeleted(rs.getBoolean("isDeleted"))
                .rowVersion(rs.getInt("rowVersion"))
                .auditDetails(auditDetails)
                .build();
    }
}
