package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovSearchModel;
import org.springframework.validation.annotation.Validated;

/**
* ProjectSearch
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectSearch extends EgovSearchModel {

    @JsonProperty("startDate")
    private Long startDate = null;

    @JsonProperty("endDate")
    private Long endDate = null;

    @JsonProperty("isTaskEnabled")
    private Boolean isTaskEnabled = false;

    @JsonProperty("parent")
    @Size(min=2,max=64)
    private String parent = null;

    @JsonProperty("projectTypeId")
    private String projectTypeId = null;

    @JsonProperty("subProjectTypeId")
    private String subProjectTypeId = null;

    @JsonProperty("department")
    @Size(min=2,max=64)
    private String department = null;

    @JsonProperty("referenceId")
    @Size(min=2,max=100)
    private String referenceId = null;

    @JsonProperty("boundaryCode")
    private String boundaryCode = null;

}

