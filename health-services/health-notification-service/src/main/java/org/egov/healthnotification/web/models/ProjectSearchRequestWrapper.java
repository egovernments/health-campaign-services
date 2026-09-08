package org.egov.healthnotification.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.project.Project;

import java.util.List;

/**
 * Wrapper for Project search API request that matches the actual API format.
 * The API expects "Projects" array, not "Project" object.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectSearchRequestWrapper {

    @JsonProperty("RequestInfo")
    @Valid
    private RequestInfo requestInfo;

    @JsonProperty("Projects")
    @Valid
    private List<Project> projects;

    @JsonProperty("apiOperation")
    @Builder.Default
    private String apiOperation = "SEARCH";
}
