package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
* A representation of a household member (already registered as an individual)
*/
    @ApiModel(description = "A representation of a household member (already registered as an individual)")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-21T13:41:16.379+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdMember   {
        @JsonProperty("id")
    


    private String id = null;

        @JsonProperty("householdId")
      @NotNull



    private String householdId = null;

        @JsonProperty("householdClientReferenceId")
    


    private String householdClientReferenceId = null;

        @JsonProperty("individualId")
      @NotNull



    private String individualId = null;

        @JsonProperty("individualClientReferenceId")
    


    private String individualClientReferenceId = null;

        @JsonProperty("isHeadOfHousehold")
    


    private Boolean isHeadOfHousehold = false;

        @JsonProperty("tenantId")
    


    private String tenantId = null;

        @JsonProperty("additionalFields")
    
  @Valid


    private AdditionalFields additionalFields = null;

        @JsonProperty("isDeleted")
    


    private Boolean isDeleted = null;

        @JsonProperty("rowVersion")
    


    private Integer rowVersion = null;

        @JsonProperty("auditDetails")
    
  @Valid


    private AuditDetails auditDetails = null;


}

