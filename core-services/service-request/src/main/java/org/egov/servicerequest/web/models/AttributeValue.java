package org.egov.servicerequest.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Hold the attribute details as object.
 */
@Schema(description = "Hold the attribute details as object.")
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AttributeValue {
    @JsonProperty("id")
    @Size(min = 2, max = 64)
    private String id = null;

    @JsonProperty("clientReferenceId")
    @Size(min = 2, max = 64)
    private String clientReferenceId = null;

    @JsonProperty("referenceId")
    @Size(min = 2, max = 64)
    private String referenceId = null;

    @JsonProperty("serviceClientReferenceId")
    @Size(min = 2, max = 64)
    private String serviceClientReferenceId = null;

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
