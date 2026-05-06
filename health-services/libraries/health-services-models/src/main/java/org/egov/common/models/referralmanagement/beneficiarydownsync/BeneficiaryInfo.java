package org.egov.common.models.referralmanagement.beneficiarydownsync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BeneficiaryInfo {

    @JsonProperty("id")
    private String id;

    @JsonProperty("householdClientReferenceId")
    private String householdClientReferenceId;

    @JsonProperty("givenName")
    private String givenName;

    @JsonProperty("identifierType")
    private String identifierType;

    @JsonProperty("identifierId")
    private String identifierId;

    @JsonProperty("isHead")
    private Boolean isHead;

    @JsonProperty("status")
    private String status;

    @JsonProperty("taskStatus")
    private String taskStatus;

    @JsonProperty("mobileNumber")
    private String mobileNumber;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("auditCreatedBy")
    private String auditCreatedBy;

    @JsonProperty("nonRecoverableError")
    @Builder.Default
    private Boolean nonRecoverableError = Boolean.FALSE;

    @JsonProperty("auditCreatedTime")
    private Long auditCreatedTime;

    @JsonProperty("clientCreatedTime")
    private Long clientCreatedTime;

    @JsonProperty("clientModifiedBy")
    private String clientModifiedBy;

    @JsonProperty("clientCreatedBy")
    private String clientCreatedBy;

    @JsonProperty("clientModifiedTime")
    private Long clientModifiedTime;

    @JsonProperty("auditModifiedBy")
    private String auditModifiedBy;

    @JsonProperty("auditModifiedTime")
    private Long auditModifiedTime;

    @JsonProperty("clientReferenceId")
    private String clientReferenceId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("isDeleted")
    @Builder.Default
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("rowVersion")
    private Integer rowVersion;

    @JsonProperty("additionalFields")
    private String additionalFields;
}
