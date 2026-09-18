package org.egov.healthnotification.web.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovModel;
import org.egov.healthnotification.web.models.enums.NotificationStatus;
import org.egov.healthnotification.web.models.enums.RecipientType;

import java.time.LocalDate;

/**
 * Model representing a scheduled notification that will be sent at a specific time.
 * This is used to store notifications that need to be sent in the future based on
 * certain events (e.g., stock distribution, campaign milestones).
 *
 * Extends EgovModel which provides:
 *   id, tenantId, rowVersion, auditDetails, hasErrors, additionalFields
 *
 * Compatible with GenericRepository contract and populateErrorDetails/handleErrors pattern.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel(description = "Scheduled notification details")
public class ScheduledNotification extends EgovModel {

    @JsonProperty("clientReferenceId")
    @Size(max = 64)
    private String clientReferenceId;

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

    // Scheduling (DATE only — no time component; scheduler picks by date)
    @JsonProperty("scheduledAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @NotNull
    private LocalDate scheduledAt;

    @JsonProperty("createdAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate createdAt;

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
    @Size(max = 255)
    private String mobileNumber;

    @JsonProperty("contextData")
    @NotNull
    private Object contextData;

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

    @JsonProperty("isDeleted")
    @Builder.Default
    private Boolean isDeleted = Boolean.FALSE;
}
