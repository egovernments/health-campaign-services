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
* ProjectSearch
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectSearch   {
        @JsonProperty("id")
    


    private String id = null;

        @JsonProperty("tenantId")
    


    private String tenantId = null;

        @JsonProperty("startDate")
    


    private Long startDate = null;

        @JsonProperty("endDate")
    


    private Long endDate = null;

        @JsonProperty("isTaskEnabled")
    


    private Boolean isTaskEnabled = false;

        @JsonProperty("parent")
    

    @Size(min=2,max=64) 

    private String parent = null;

        @JsonProperty("projectTypeId")
    


    private String projectTypeId = null;

        @JsonProperty("subProjectTypeId")
    


    private String subProjectTypeId = null;

        @JsonProperty("department")
    

    @Size(min=2,max=64) 

    private String department = null;

        @JsonProperty("referenceId")
    

    @Size(min=2,max=100) 

    private String referenceId = null;

        @JsonProperty("boundaryCode")
    


    private String boundaryCode = null;


}

