package org.egov.common.models.project;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

/**
 * Represents a bulk response for project facilities, including response metadata and a list of facilities.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectFacilityBulkResponse {

    /**
     * Metadata about the API response, including request details and status.
     */
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    /**
     * Total number of project facilities in the response, defaults to 0.
     */
    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

    /**
     * List of project facilities returned in the response.
     */
    @JsonProperty("ProjectFacilities")
    @NotNull
    @Valid
    private List<ProjectFacility> projectFacilities = new ArrayList<>();

    /**
     * Adds a single project facility to the list and returns the updated response.
     */
    public ProjectFacilityBulkResponse addProjectFacilityItem(ProjectFacility projectFacilityItem) {
        this.projectFacilities.add(projectFacilityItem);
        return this;
    }
}

