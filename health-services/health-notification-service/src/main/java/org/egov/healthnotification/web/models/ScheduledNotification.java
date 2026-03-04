package org.egov.healthnotification.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.models.AuditDetails;
import org.egov.healthnotification.web.models.enums.NotificationStatus;
import org.egov.healthnotification.web.models.enums.RecipientType;

import java.util.Map;

/**
 * Model representing a scheduled notification that will be sent at a specific time.
 * This is used to store notifications that need to be sent in the future based on
 * certain events (e.g., stock distribution, campaign milestones).
 *
 * Compatible with GenericRepository contract — requires:
 * id, tenantId, clientReferenceId, isDeleted, rowVersion, auditDetails
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel(description = "Scheduled notification details")
public class ScheduledNotification {

    @JsonProperty("id")
    @Size(max = 64)
    private String id;

    @JsonProperty("clientReferenceId")
    @Size(max = 64)
    private String clientReferenceId;

    @JsonProperty("tenantId")
    @NotBlank
    @Size(max = 64)
    private String tenantId;

    // Trigger context
    @JsonProperty("eventType")
    @NotBlank
    @Size(max = 64)
    private String eventType;

    @JsonProperty("entityId")
    @NotBlank
    @Size(max = 64)
    private String entityId;

    @JsonProperty("entityType")
    @NotBlank
    @Size(max = 64)
    private String entityType;

    // Scheduling
    @JsonProperty("scheduledAt")
    @NotNull
    private Long scheduledAt;

    @JsonProperty("createdAt")
    private Long createdAt;

    // Notification details
    @JsonProperty("templateCode")
    @NotBlank
    @Size(max = 128)
    private String templateCode;

    @JsonProperty("recipientType")
    @NotNull
    private RecipientType recipientType;

    @JsonProperty("recipientId")
    @NotBlank
    @Size(max = 64)
    private String recipientId;

    @JsonProperty("mobileNumber")
    @Size(max = 20)
    private String mobileNumber;

    // Message context (JSON with placeholder data)
    @JsonProperty("contextData")
    @NotNull
    private Map<String, Object> contextData;

    // Status tracking
    @JsonProperty("status")
    @NotNull
    private NotificationStatus status;

    @JsonProperty("attempts")
    @Builder.Default
    private Integer attempts = 0;

    @JsonProperty("lastAttemptAt")
    private Long lastAttemptAt;

    @JsonProperty("errorMessage")
    private String errorMessage;

    // GenericRepository contract fields
    @JsonProperty("isDeleted")
    @Builder.Default
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("rowVersion")
    @Builder.Default
    private Integer rowVersion = 1;

    // Audit details
    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails;
}
