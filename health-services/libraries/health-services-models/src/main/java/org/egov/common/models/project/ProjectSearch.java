package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Size;

/**
* ProjectSearch
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectSearch {

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    private String tenantId = null;

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

