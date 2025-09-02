package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.core.AdditionalFields;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserActionIndexV1 {
    
    @JsonProperty("id")
    private String id;

    @JsonProperty("projectId")
    private String projectId;

    @JsonProperty("projectType")
    private String projectType;

    @JsonProperty("projectTypeId")
    private String projectTypeId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("locationAccuracy")
    private Double locationAccuracy;

    @JsonProperty("boundaryCode")
    private String boundaryCode;

    @JsonProperty("action")
    private String action;

    @JsonProperty("beneficiaryTag")
    private String beneficiaryTag;

    @JsonProperty("resourceTag")
    private String resourceTag;

    @JsonProperty("createdTime")
    private Long createdTime;

    @JsonProperty("createdBy")
    private String createdBy;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;

    @JsonProperty("clientAuditDetails")
    private AuditDetails clientAuditDetails;

    @JsonProperty("additionalFields")
    private AdditionalFields additionalFields;

    @JsonProperty("additionalDetails")
    private JsonNode additionalDetails;

    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;

    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;

    @JsonProperty("isDeleted")
    private Boolean isDeleted;

    @JsonProperty("source")
    private String source;

    @JsonProperty("rowVersion")
    private Integer rowVersion;

    @JsonProperty("applicationId")
    private String applicationId;

    @JsonProperty("hasErrors")
    private Boolean hasErrors;

    @JsonProperty("clientReferenceId")
    private String clientReferenceId;

    @JsonProperty("geoPoint")
    private Double[] geoPoint;
}