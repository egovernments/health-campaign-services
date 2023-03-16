package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.data.query.annotations.Table;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
* This object defines the mapping of a facility to a project.
*/
    @ApiModel(description = "This object defines the mapping of a facility to a project.")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
@Table(name="project_facility")
public class ProjectFacilitySearch   {

    @JsonProperty("id")
    private List<String> id = null;

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("facilityId")
    private List<String> facilityId = null;

    @JsonProperty("projectId")
    private List<String> projectId = null;

}