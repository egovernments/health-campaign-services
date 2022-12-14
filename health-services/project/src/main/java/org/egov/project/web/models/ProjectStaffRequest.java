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
* ProjectStaffRequest
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectStaffRequest   {
        @JsonProperty("RequestInfo")
      @NotNull

  @Valid


    private RequestInfo requestInfo = null;

        @JsonProperty("ProjectStaff")
      @NotNull

  @Valid


    private List<ProjectStaff> projectStaff = new ArrayList<>();

        @JsonProperty("apiOperation")
    
  @Valid


    private ApiOperation apiOperation = null;


        public ProjectStaffRequest addProjectStaffItem(ProjectStaff projectStaffItem) {
        this.projectStaff.add(projectStaffItem);
        return this;
        }

}

