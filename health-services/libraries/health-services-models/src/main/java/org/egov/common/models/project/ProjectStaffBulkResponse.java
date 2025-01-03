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
* ProjectStaffResponse
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectStaffBulkResponse {

    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("ProjectStaff")
    @NotNull
    @Valid
    private List<ProjectStaff> projectStaff = new ArrayList<>();

    public ProjectStaffBulkResponse addProjectStaffItem(ProjectStaff projectStaffItem) {
        this.projectStaff.add(projectStaffItem);
        return this;
    }
}

