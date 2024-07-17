package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.egov.tracer.model.AuditDetails;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineModel;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
* A representation of the registration of an entity to a Project.
*/
    @ApiModel(description = "A representation of the registration of an entity to a Project.")
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectBeneficiary extends EgovOfflineModel {

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
    /*
    * This is the client reference id of the beneficiary type entity (i.e. household, individual)
    * */
    @JsonProperty("beneficiaryClientReferenceId")
    @Size(min=2,max=64)
    private String beneficiaryClientReferenceId = null;

    //TODO remove this
    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("tag")
    private String tag;

}
