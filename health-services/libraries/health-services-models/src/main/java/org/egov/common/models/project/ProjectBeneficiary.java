package org.egov.common.models.project;

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
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
* A representation of the registration of an entity to a Project.
*/
    @ApiModel(description = "A representation of the registration of an entity to a Project.")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectBeneficiary {
    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min=2,max=64)
    private String tenantId = null;

    @JsonProperty("projectId")
    @NotNull
    @Size(min=2,max=64)
    private String projectId = null;

    @JsonProperty("beneficiaryId")
    @Size(min=2,max=64)
    private String beneficiaryId = null;

    @JsonProperty("dateOfRegistration")
    @Min(value = 0, message = "Date must be greater than or equal to 0")
    private Long dateOfRegistration = null;

    @JsonProperty("clientReferenceId")
    @Size(min=2,max=64)
    private String clientReferenceId = null;

    @JsonProperty("beneficiaryClientReferenceId")
    @Size(min=2,max=64)
    private String beneficiaryClientReferenceId = null;

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

    @JsonProperty("tag")
    private String tag;

}
