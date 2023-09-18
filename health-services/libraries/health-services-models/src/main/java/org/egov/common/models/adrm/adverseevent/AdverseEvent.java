package org.egov.common.models.adrm.adverseevent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdverseEvent {

    @JsonProperty("id")
    @Size(min = 2, max = 64)
    private String id = null;

    @JsonProperty("clientReferenceId")
    @Size(min = 2, max = 64)
    private String clientReferenceId = null;

    @JsonProperty("taskId")
    @Size(min = 2, max = 64)
    private String taskId = null;

    @JsonProperty("taskClientReferenceId")
    @Size(min = 2, max = 64)
    private String taskClientReferenceId = null;

    @JsonProperty("symptoms")
    @NotNull
    @Size(min=1)
    private List<String> symptoms = null;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min=2, max = 1000)
    private String tenantId = null;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("rowVersion")
    private Integer rowVersion = null;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;

    @JsonProperty("clientAuditDetails")
    @Valid
    private AuditDetails clientAuditDetails = null;

    @JsonIgnore
    private Boolean hasErrors = Boolean.FALSE;
}
