package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.request.RequestInfo;

/**
* ProjectFacilityRequest
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectFacilityRequest   {
        @JsonProperty("RequestInfo")
      @NotNull

  @Valid


    private RequestInfo requestInfo = null;

        @JsonProperty("ProjectFacility")
      @NotNull

  @Valid


    private List<ProjectFacility> projectFacility = new ArrayList<>();

        @JsonProperty("apiOperation")
    
  @Valid


    private ApiOperation apiOperation = null;


        public ProjectFacilityRequest addProjectFacilityItem(ProjectFacility projectFacilityItem) {
        this.projectFacility.add(projectFacilityItem);
        return this;
        }

}

