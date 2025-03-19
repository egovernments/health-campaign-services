package org.egov.project.web.models.mdmsv2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.Size;

@Validated
@JsonIgnoreProperties(ignoreUnknown = true)

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Mdms {
    @JsonProperty("id")
    @Size(min = 2, max = 64)
    private String id;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 128)
    private String tenantId = null;

    @JsonProperty("schemaCode")
    @NotNull
    @Size(min = 2, max = 128)
    private String schemaCode = null;

    @JsonProperty("uniqueIdentifier")
    @Size(min = 2, max = 128)
    private String uniqueIdentifier = null;

    @JsonProperty("data")
    @NotNull
    private JsonNode data = null;

    @JsonProperty("isActive")
    private Boolean isActive = true;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;
}
