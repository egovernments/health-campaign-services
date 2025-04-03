package org.egov.common.models.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import org.egov.common.contract.models.AuditDetails;

/**
 * Hold the attribute details as object.
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AttributeValue {
    @JsonProperty("id")
    private String id = null;

    @JsonProperty("clientReferenceId")
    @Size(min = 2, max = 64)
    private String clientReferenceId = null;

    @JsonProperty("referenceId")
    @Size(min = 2, max = 64)
    private String referenceId = null;

    @JsonProperty("attributeCode")
    @NotNull
    private String attributeCode = null;

    @JsonProperty("value")
    private Object value = null;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;

    @JsonProperty("additionalFields")
    private Object additionalDetails = null;

}
