package org.egov.project.web.models;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.egov.project.web.models.AuditDetails;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
* Target
*/
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Target   {
        @JsonProperty("id")
    

    @Size(min=2,max=64) 

    private String id = null;

        @JsonProperty("beneficiaryType")
      @NotNull


    @Size(min=2,max=64) 

    private String beneficiaryType = null;

        @JsonProperty("baseline")
      @NotNull



    private Integer baseline = null;

        @JsonProperty("target")
      @NotNull



    private Integer target = null;

        @JsonProperty("isDeleted")
    


    private Boolean isDeleted = null;

        @JsonProperty("auditDetails")
    
  @Valid


    private AuditDetails auditDetails = null;


}

