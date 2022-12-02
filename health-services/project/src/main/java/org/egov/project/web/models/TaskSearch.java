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
* TaskSearch
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskSearch   {
        @JsonProperty("id")
    


    private String id = null;

        @JsonProperty("tenantId")
    


    private String tenantId = null;

        @JsonProperty("projectId")
    

    @Size(min=2,max=64) 

    private String projectId = null;

        @JsonProperty("projectBeneficiaryId")
    


    private String projectBeneficiaryId = null;

        @JsonProperty("plannedStartDate")
    


    private Long plannedStartDate = null;

        @JsonProperty("plannedEndDate")
    


    private Long plannedEndDate = null;

        @JsonProperty("actualStartDate")
    


    private Long actualStartDate = null;

        @JsonProperty("actualEndDate")
    


    private Long actualEndDate = null;

        @JsonProperty("createdBy")
    


    private String createdBy = null;

        @JsonProperty("status")
    


    private String status = null;

        @JsonProperty("boundaryCode")
    


    private String boundaryCode = null;


}

