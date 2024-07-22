package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovModel;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
* This object defines the mapping of a resource to a project.
*/
    @ApiModel(description = "This object defines the mapping of a resource to a project.")
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectResource extends EgovModel {

    @JsonProperty("projectId")
    @NotNull
    @Size(min=2,max=64)
    private String projectId = null;

    @JsonProperty("resource")
    @NotNull
    private ProjectProductVariant resource = null;

    //TODO remove
    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("startDate")
    private Long startDate = null;

    @JsonProperty("endDate")
    private Long endDate = null;

}

