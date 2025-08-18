package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

import org.egov.common.contract.models.AuditDetails;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GenerateResource {

    @JsonProperty("id")
    private String id;

    @JsonProperty("tenantId")
    @NotBlank(message = "INGEST_MISSING_TENANT_ID")
    @Size(min = 2, max = 50, message = "INGEST_INVALID_TENANT_ID_LENGTH")
    private String tenantId;

    @JsonProperty("type")
    @NotBlank(message = "INGEST_MISSING_TYPE")
    @Size(min = 2, max = 100, message = "INGEST_INVALID_TYPE_LENGTH")
    private String type;

    @JsonProperty("hierarchyType")
    @NotBlank(message = "INGEST_MISSING_HIERARCHY_TYPE")
    @Size(min = 2, max = 100, message = "INGEST_INVALID_HIERARCHY_TYPE_LENGTH")
    private String hierarchyType;

    @JsonProperty("referenceId")
    @NotBlank(message = "INGEST_MISSING_REFERENCE_ID")
    @Size(min = 1, max = 255, message = "INGEST_INVALID_REFERENCE_ID_LENGTH")
    private String referenceId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("additionalDetails")
    private Map<String, Object> additionalDetails;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;

    @JsonProperty("fileStoreId")
    private String fileStoreId;

    // Getter for boundaries from additionalDetails
    @JsonIgnore
    public List<Boundary> getBoundaries() {
        if (additionalDetails != null && additionalDetails.containsKey("boundaries")) {
            Object boundariesObj = additionalDetails.get("boundaries");
            if (boundariesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> boundariesList = (List<Object>) boundariesObj;
                return boundariesList.stream()
                        .map(this::mapToBoundary)
                        .filter(boundary -> boundary != null)
                        .collect(java.util.stream.Collectors.toList());
            }
        }
        return null;
    }

    // Helper method to convert Map to Boundary object
    private Boundary mapToBoundary(Object obj) {
        if (obj instanceof Boundary) {
            return (Boundary) obj;
        } else if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            return Boundary.builder()
                    .code((String) map.get("code"))
                    .name((String) map.get("name"))
                    .type((String) map.get("type"))
                    .isRoot((Boolean) map.get("isRoot"))
                    .parent((String) map.get("parent"))
                    .includeAllChildren((Boolean) map.get("includeAllChildren"))
                    .build();
        }
        return null;
    }

    // Setter for boundaries in additionalDetails
    public void setBoundaries(List<Boundary> boundaries) {
        if (additionalDetails == null) {
            additionalDetails = new HashMap<>();
        }
        if (boundaries != null) {
            additionalDetails.put("boundaries", boundaries);
        } else {
            additionalDetails.remove("boundaries");
        }
    }
}
