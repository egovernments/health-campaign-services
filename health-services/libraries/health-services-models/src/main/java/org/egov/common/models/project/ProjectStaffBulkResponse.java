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
 * Represents a bulk response for project staff, containing response metadata and a list of staff members.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectStaffBulkResponse {

    /**
     * Metadata about the API response, including request status and other information.
     */
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    /**
     * List of project staff members returned in the response.
     */
    @JsonProperty("ProjectStaff")
    @NotNull
    @Valid
    private List<ProjectStaff> projectStaff = new ArrayList<>();

    /**
     * Total number of project staff members in the response, defaults to 0 if not specified.
     */
    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

    /**
     * Adds a single project staff member to the list and returns the updated response.
     *
     * @param projectStaffItem The project staff member to add to the list.
     * @return The updated ProjectStaffBulkResponse instance.
     */
    public ProjectStaffBulkResponse addProjectStaffItem(ProjectStaff projectStaffItem) {
        this.projectStaff.add(projectStaffItem);
        return this;
    }
}
