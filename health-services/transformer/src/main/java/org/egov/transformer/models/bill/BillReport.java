package org.egov.transformer.models.bill;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.tracer.model.AuditDetails;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillReport {

    @JsonProperty("id")
    private String id;

    @JsonProperty("billId")
    private String billId;

    @JsonProperty("billIds")
    private List<String> billIds;

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId;

    @JsonProperty("type")
    @NotNull
    private ReportType type;

    @JsonProperty("status")
    private ReportStatus status;

    @JsonProperty("fileStoreId")
    private String fileStoreId;

    @JsonProperty("errorDetails")
    private Object errorDetails;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails;
}
