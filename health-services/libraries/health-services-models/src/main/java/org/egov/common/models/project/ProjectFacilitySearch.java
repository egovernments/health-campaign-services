package org.egov.common.models.project;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovSearchModel;
import org.springframework.validation.annotation.Validated;

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
public class ProjectFacilitySearch extends EgovSearchModel {

    @JsonProperty("facilityId")
    private List<String> facilityId = null;

    @JsonProperty("projectId")
    private List<String> projectId = null;

}