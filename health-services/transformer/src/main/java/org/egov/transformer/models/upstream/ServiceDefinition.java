package org.egov.transformer.models.upstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the Service Definition details json object.
 */

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceDefinition {
    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("code")
    private String code = null;

    @JsonProperty("isActive")
    private Boolean isActive = true;

    @JsonProperty("attributes")
    private List<AttributeDefinition> attributes = new ArrayList<>();

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

    @JsonProperty("clientId")
    private String clientId = null;


    public ServiceDefinition addAttributesItem(AttributeDefinition attributesItem) {
        this.attributes.add(attributesItem);
        return this;
    }

}
