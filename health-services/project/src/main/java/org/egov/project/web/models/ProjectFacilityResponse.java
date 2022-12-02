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
import org.egov.common.contract.response.ResponseInfo;

/**
* ProjectFacilityResponse
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectFacilityResponse   {
        @JsonProperty("ResponseInfo")
      @NotNull

  @Valid


    private ResponseInfo responseInfo = null;

        @JsonProperty("ProjectFacility")
      @NotNull

  @Valid


    private List<ProjectFacility> projectFacility = new ArrayList<>();


        public ProjectFacilityResponse addProjectFacilityItem(ProjectFacility projectFacilityItem) {
        this.projectFacility.add(projectFacilityItem);
        return this;
        }

}

