package org.egov.transformer.models.expense;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Party {
    @JsonProperty("id")
    @Valid
    private String id;

    @JsonProperty("parentId")
    @Valid
    private String parentId;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 64)
    private String tenantId;

    @JsonProperty("type")
    @NotNull
    @Size(min = 2, max = 64)
    private String type;

    @JsonProperty("identifier")
    @NotNull
    @Size(min = 2, max = 64)
    private String identifier;

    @JsonProperty("status")
    private Status status;

    @JsonProperty("additionalDetails")
    private Object additionalDetails;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails;
}
