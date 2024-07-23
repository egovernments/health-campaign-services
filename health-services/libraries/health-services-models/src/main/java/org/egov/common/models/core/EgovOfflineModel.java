package org.egov.common.models.core;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.egov.common.contract.models.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class EgovOfflineModel extends EgovModel {
    @JsonProperty("clientReferenceId")
    @Size(min = 2, max = 64)
    protected String clientReferenceId;

    @JsonProperty("clientAuditDetails")
    @Valid
    protected AuditDetails clientAuditDetails;
}
