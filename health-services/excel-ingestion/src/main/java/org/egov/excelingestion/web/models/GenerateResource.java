package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
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

    @JsonProperty("refernceId")
    @NotBlank(message = "INGEST_MISSING_REFERENCE_ID")
    @Size(min = 1, max = 255, message = "INGEST_INVALID_REFERENCE_ID_LENGTH")
    private String refernceId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("additionalDetails")
    private Map<String, Object> additionalDetails;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;

    @JsonProperty("fileStoreId")
    private String fileStoreId;

    @JsonProperty("boundaries")
    @Valid
    private List<Boundary> boundaries;
}
