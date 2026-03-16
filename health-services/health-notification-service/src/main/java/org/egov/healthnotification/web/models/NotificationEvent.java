package org.egov.healthnotification.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.healthnotification.web.models.enums.NotificationChannel;

import java.util.List;
import java.util.Map;

/**
 * Generic notification event contract between service-specific adapters
 * and the generic NotificationProcessorService.
 *
 * Service-specific adapters (e.g., StockNotificationAdapter) build this object,
 * and the generic processor resolves templates and sends the notification.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationEvent {

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("entityType")
    private String entityType;

    @JsonProperty("entityId")
    private String entityId;

    @JsonProperty("templateCode")
    private String templateCode;

    @JsonProperty("titleTemplateCode")
    private String titleTemplateCode;

    @JsonProperty("locale")
    private String locale;

    @JsonProperty("recipientUserUuids")
    private List<String> recipientUserUuids;

    @JsonProperty("placeholders")
    private Map<String, Object> placeholders;

    @JsonProperty("data")
    private Map<String, String> data;

    @JsonProperty("channel")
    private NotificationChannel channel;
}
