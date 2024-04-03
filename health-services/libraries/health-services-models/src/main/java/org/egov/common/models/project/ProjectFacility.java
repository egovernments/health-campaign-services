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
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovModel;
import org.egov.common.models.core.EgovOfflineModel;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
* This object defines the mapping of a facility to a project.
*/
@ApiModel(description = "This object defines the mapping of a facility to a project.")
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectFacility extends EgovModel {
//    @JsonProperty("id")
//    @Size(min = 2, max = 64)
//    private String id = null;
//
//    @JsonProperty("tenantId")
//    @NotNull
//    @Size(min = 2, max = 1000)
//    private String tenantId = null;

    @JsonProperty("facilityId")
    @NotNull
    @Size(min=2,max=64)
    private String facilityId = null;

    @JsonProperty("projectId")
    @NotNull
    @Size(min=2,max=64)
    private String projectId = null;

    //TODO remove
    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

//    @JsonProperty("rowVersion")
//    private Integer rowVersion = null;
//
//    @JsonProperty("additionalFields")
//    @Valid
//    private AdditionalFields additionalFields = null;
//
//    @JsonIgnore
//    private Boolean hasErrors = Boolean.FALSE;
//
//    @JsonProperty("auditDetails")
//    @Valid
//    private AuditDetails auditDetails = null;

}