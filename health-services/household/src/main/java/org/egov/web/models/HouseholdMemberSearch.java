package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;

/**
* Search model for household member
*/
    @ApiModel(description = "Search model for household member")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-21T13:41:16.379+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdMemberSearch   {
        @JsonProperty("id")
    


    private String id = null;

        @JsonProperty("householdId")
    


    private String householdId = null;

        @JsonProperty("householdClientReferenceId")
    


    private String householdClientReferenceId = null;

        @JsonProperty("individualId")
    


    private String individualId = null;

        @JsonProperty("individualClientReferenceId")
    


    private String individualClientReferenceId = null;

        @JsonProperty("isHeadOfHousehold")
    


    private Boolean isHeadOfHousehold = null;

        @JsonProperty("tenantId")
    
  @Valid


    private String tenantId = null;


}

