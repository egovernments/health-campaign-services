package org.egov.common.models.household;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
* A representation of a household member (already registered as an individual)
*/
    @ApiModel(description = "A representation of a household member (already registered as an individual)")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-21T13:41:16.379+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
public class HouseholdMember{

    @JsonProperty("id")
    @Size(min = 2, max = 64)
    private String id = null;

    @JsonProperty("householdId")
    @Size(min = 2, max = 64)
    private String householdId = null;

    @JsonProperty("householdClientReferenceId")
    @Size(min = 2, max = 64)
    private String householdClientReferenceId = null;

    @JsonProperty("clientReferenceId")
    @Size(min = 2, max = 64)
    @NotNull
    private String clientReferenceId = null;

    @JsonProperty("individualId")
    @Size(min = 2, max = 64)
    private String individualId = null;

    @JsonProperty("individualClientReferenceId")
    @Size(min = 2, max = 64)
    private String individualClientReferenceId = null;

    @JsonProperty("isHeadOfHousehold")
    private Boolean isHeadOfHousehold = false;

    @JsonProperty("tenantId")
    @Size(min = 2, max = 1000)
    @NotNull
    private String tenantId = null;

    @JsonProperty("additionalFields")
    @Valid
    private AdditionalFields additionalFields = null;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("rowVersion")
    private Integer rowVersion = null;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;

    @JsonProperty("clientAuditDetails")
    @Valid
    private AuditDetails clientAuditDetails = null;

    @JsonIgnore
    private Boolean hasErrors = Boolean.FALSE;
}

