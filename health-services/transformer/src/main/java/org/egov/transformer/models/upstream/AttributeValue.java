package org.egov.transformer.models.upstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

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

    @JsonProperty("referenceId")
    private String referenceId = null;

    @JsonProperty("attributeCode")
    private String attributeCode = null;

    @JsonProperty("value")
    private Object value = null;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;

    @JsonProperty("additionalFields")
    private AdditionalFields additionalFields;
}
