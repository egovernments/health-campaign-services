package org.egov.household.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
* Identifier
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-27T11:47:19.561+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Identifier {
    @JsonProperty("id")
    @Size(min=2,max=64)
    private String id = null;

    @JsonProperty("individualId")
    @Size(min=2,max=64)
    private String individualId = null;

    @JsonProperty("identifierType")
    @NotNull
    @Size(min=2,max=64)
    private String identifierType = null;

    @JsonProperty("identifierId")
    @NotNull
    @Size(min=2,max=64)
    private String identifierId = null;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = null;

    @JsonProperty("rowVersion")
    private Integer rowVersion = null;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;

}

