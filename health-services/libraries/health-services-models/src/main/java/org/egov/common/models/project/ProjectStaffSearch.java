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
 * This object defines the mapping of a system staff user to a project for a certain period.
 */
@ApiModel(description = "This object defines the mapping of a system staff user to a project for a certain period.")
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectStaffSearch extends EgovSearchModel {

    @JsonProperty("staffId")
    private List<String> staffId = null;

    @JsonProperty("projectId")
    private List<String> projectId = null;

    @JsonProperty("startDate")
    private Long startDate = null;

    @JsonProperty("endDate")
    private Long endDate = null;


}

