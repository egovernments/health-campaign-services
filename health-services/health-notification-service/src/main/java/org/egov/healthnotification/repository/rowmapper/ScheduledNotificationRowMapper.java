package org.egov.healthnotification.repository.rowmapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.web.models.ScheduledNotification;
import org.egov.healthnotification.web.models.enums.NotificationStatus;
import org.egov.healthnotification.web.models.enums.RecipientType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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

        // Parse contextData JSON (can be Map or encrypted String)
        Object contextData = null;
        String contextDataStr = rs.getString("contextData");
        if (!ObjectUtils.isEmpty(contextDataStr)) {
            try {
                // Check if it's encrypted (JSONB stores encrypted string as "prefix|base64data" with quotes)
                // When encrypted, contextDataStr will be a JSON string like: "104227|base64..."
                if (contextDataStr.trim().startsWith(Constants.JSON_QUOTE)
                        && contextDataStr.contains(Constants.CIPHER_TEXT_SEPARATOR)) {
                    // It's encrypted - remove surrounding quotes and store as String
                    contextData = objectMapper.readValue(contextDataStr, String.class);
                } else {
                    // It's not encrypted - parse as Map
                    contextData = objectMapper.readValue(contextDataStr,
                            new TypeReference<Map<String, Object>>() {});
                }
            } catch (Exception e) {
                log.error("Error parsing contextData JSON for notification: {}", rs.getString("id"), e);
                // Fallback: store as empty Map
                contextData = new HashMap<>();
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
                .scheduledAt(toLocalDate(rs.getDate("scheduledAt")))
                .createdAt(toLocalDate(rs.getDate("createdAt")))
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

    private LocalDate toLocalDate(Date sqlDate) {
        return sqlDate != null ? sqlDate.toLocalDate() : null;
    }
}
