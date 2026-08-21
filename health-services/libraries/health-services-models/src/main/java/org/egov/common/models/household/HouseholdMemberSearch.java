package org.egov.common.models.household;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineSearchModel;
import org.springframework.validation.annotation.Validated;

/**
* Search model for household member
*/
    @ApiModel(description = "Search model for household member")
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HouseholdMemberSearch  extends EgovOfflineSearchModel {

    @JsonProperty("clientReferenceId")
    private List<String> clientReferenceId = null;

    @JsonProperty("householdId")
    private List<String> householdId = null;

    @JsonProperty("householdClientReferenceId")
    private List<String> householdClientReferenceId = null;

    @JsonProperty("individualId")
    private List<String> individualId = null;

    @JsonProperty("individualClientReferenceId")
    private List<String> individualClientReferenceId = null;

    @JsonProperty("isHeadOfHousehold")
    private Boolean isHeadOfHousehold = null;

}

