package org.egov.project.web.models;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
* This object defines the mapping of a system staff user to a project for a certain period.
*/
    @ApiModel(description = "This object defines the mapping of a system staff user to a project for a certain period.")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectStaffSearch   {
        @JsonProperty("id")
    


    private String id = null;

        @JsonProperty("tenantId")
    


    private String tenantId = null;

        @JsonProperty("userId")
    

    @Size(min=2,max=64) 

    private String userId = null;

        @JsonProperty("projectId")
    

    @Size(min=2,max=64) 

    private String projectId = null;

        @JsonProperty("startDate")
    


    private Long startDate = null;

        @JsonProperty("endDate")
    


    private Long endDate = null;


}

