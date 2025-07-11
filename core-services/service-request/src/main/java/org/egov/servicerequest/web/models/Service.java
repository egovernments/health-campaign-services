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
import java.util.ArrayList;
import java.util.List;

/**
 * Hold the Service field details as json object.
 */
@Schema(description = "Hold the Service field details as json object.")
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Service {
    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 64)
    private String tenantId = null;

    @JsonProperty("serviceDefId")
    @NotNull
    @Size(min = 2, max = 64)
    private String serviceDefId = null;

    @JsonProperty("referenceId")
    @Size(min = 2, max = 64)
    private String referenceId = null;

    @JsonProperty("attributes")
    @NotNull
    @Valid
    private List<AttributeValue> attributes = new ArrayList<>();

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;

    @JsonProperty("additionalFields")
    private Object additionalDetails = null;

    @JsonProperty("accountId")
    @NotNull
    @Size(max = 64)
    private String accountId = null;

    @JsonProperty("clientId")
    @Size(max = 64)
    private String clientId = null;


    public Service addAttributesItem(AttributeValue attributesItem) {
        this.attributes.add(attributesItem);
        return this;
    }

}
