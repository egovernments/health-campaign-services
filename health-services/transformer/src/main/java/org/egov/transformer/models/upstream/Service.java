package org.egov.transformer.models.upstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Hold the Service field details as json object.
 */

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Service {
    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("serviceDefId")
    private String serviceDefId = null;

    @JsonProperty("referenceId")
    private String referenceId = null;

    @JsonProperty("attributes")
    private List<AttributeValue> attributes = new ArrayList<>();

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;

    @JsonProperty("additionalFields")
    private JsonNode additionalDetails = null;

    @JsonProperty("accountId")
    private String accountId = null;

    @JsonProperty("clientId")
    private String clientId = null;


    public Service addAttributesItem(AttributeValue attributesItem) {
        this.attributes.add(attributesItem);
        return this;
    }

}
