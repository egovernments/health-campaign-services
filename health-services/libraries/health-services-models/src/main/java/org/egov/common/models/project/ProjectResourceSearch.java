package org.egov.common.models.project;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
* This object defines the mapping of a resource to a project.
*/
    @ApiModel(description = "This object defines the mapping of a resource to a project.")
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectResourceSearch {

    @JsonProperty("id")
    private List<String> id = null;

    @JsonProperty("projectId")
    private List<String> projectId = null;

}

