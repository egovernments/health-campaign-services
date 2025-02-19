package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
* ProjectResponse
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectResponse {

    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("Project")
    @NotNull
    @Valid
    private List<Project> project = new ArrayList<>();

    @JsonProperty("TotalCount")
    private Integer totalCount = 0;

    public ProjectResponse addProjectItem(Project projectItem) {
        this.project.add(projectItem);
        return this;
    }

}

