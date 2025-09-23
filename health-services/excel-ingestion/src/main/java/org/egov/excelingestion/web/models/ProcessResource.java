package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

import org.apache.kafka.common.protocol.types.Field.Str;
import org.egov.common.contract.models.AuditDetails;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProcessResource {

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

    @JsonProperty("fileStoreId")
    @NotBlank(message = "INGEST_MISSING_FILE_STORE_ID")
    private String fileStoreId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("processedFileStoreId")
    private String processedFileStoreId;

    @JsonProperty("additionalDetails")
    private Map<String, Object> additionalDetails;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;

    @JsonProperty("locale")
    private String locale;
}