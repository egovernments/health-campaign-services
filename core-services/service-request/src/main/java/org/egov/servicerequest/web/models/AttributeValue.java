package org.egov.servicerequest.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

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
    private String id = null;

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

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;


}
