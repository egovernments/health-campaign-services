package org.egov.transformer.models.musterRoll;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.tracer.model.AuditDetails;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MusterRollReport {

    @JsonProperty("id")
    private String id;

    @JsonProperty("musterRollId")
    private String musterRollId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("reportType")
    private String reportType;  // From ReportType enum

    @JsonProperty("reportFormat")
    private String reportFormat;  // From ReportFormat enum

    @JsonProperty("reportStatus")
    private String reportStatus;  // INITIATED | COMPLETED | FAILED

    @JsonProperty("fileStoreId")
    private String fileStoreId;

    @JsonProperty("generatedAt")
    private Long generatedAt;

    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;
}
