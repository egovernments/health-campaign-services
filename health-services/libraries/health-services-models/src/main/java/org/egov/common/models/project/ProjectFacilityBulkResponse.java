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
 * ProjectFacilityResponse
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectFacilityBulkResponse {
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("ProjectFacilities")
    @NotNull
    @Valid
    private List<ProjectFacility> projectFacilities = new ArrayList<>();

    public ProjectFacilityBulkResponse addProjectFacilityItem(ProjectFacility projectFacilityItem) {
        this.projectFacilities.add(projectFacilityItem);
        return this;
    }
}
