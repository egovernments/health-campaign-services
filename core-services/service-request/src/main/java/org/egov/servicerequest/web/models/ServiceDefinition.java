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
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the Service Definition details json object.
 */
@Schema(description = "Holds the Service Definition details json object.")
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ServiceDefinition {
    @JsonProperty("id")
    @Size(min = 2, max = 64)
    private String id = null;

    @JsonProperty("tenantId")
    @Size(min = 2, max = 64)
    private String tenantId = null;

    @JsonProperty("code")
    @NotNull
    @Size(min = 2, max = 256)
    private String code = null;

    @JsonProperty("isActive")
    private Boolean isActive = true;

    @JsonProperty("attributes")
    @NotNull
    @Valid
    private List<AttributeDefinition> attributes = new ArrayList<>();

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

    @JsonProperty("clientId")
    @Size(max = 64)
    private String clientId = null;


    public ServiceDefinition addAttributesItem(AttributeDefinition attributesItem) {
        this.attributes.add(attributesItem);
        return this;
    }

}
