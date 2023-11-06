package org.egov.common.models.household;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.util.List;

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
    @JsonIgnoreProperties(ignoreUnknown = true)
public class HouseholdMemberSearch   {

    @JsonProperty("id")
    private List<String> id = null;

    @JsonProperty("householdId")
    private String householdId = null;

    @JsonProperty("householdClientReferenceId")
    private String householdClientReferenceId = null;

    @JsonProperty("individualId")
    private String individualId = null;

    @JsonProperty("clientReferenceId")
    private List<String> clientReferenceId = null;

    @JsonProperty("individualClientReferenceId")
    private String individualClientReferenceId = null;

    @JsonProperty("isHeadOfHousehold")
    private Boolean isHeadOfHousehold = null;

    @JsonProperty("tenantId")
    @Valid
    private String tenantId = null;

}

