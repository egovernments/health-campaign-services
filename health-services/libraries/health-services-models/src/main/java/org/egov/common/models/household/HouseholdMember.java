package org.egov.common.models.household;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineModel;
import org.springframework.validation.annotation.Validated;

/**
* A representation of a household member (already registered as an individual)
*/
    @ApiModel(description = "A representation of a household member (already registered as an individual)")
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HouseholdMember extends EgovOfflineModel {

    @JsonProperty("householdId")
    @Size(min = 2, max = 64)
    private String householdId = null;

    @JsonProperty("householdClientReferenceId")
    @Size(min = 2, max = 64)
    private String householdClientReferenceId = null;

    @JsonProperty("individualId")
    @Size(min = 2, max = 64)
    private String individualId = null;

    @JsonProperty("individualClientReferenceId")
    @Size(min = 2, max = 64)
    private String individualClientReferenceId = null;

    @JsonProperty("isHeadOfHousehold")
    private Boolean isHeadOfHousehold = false;

    @JsonProperty("relationships")
    @Valid
    private List<HouseholdMemberRelationship> relationships;

    //TODO remove
    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

    public HouseholdMember addHouseholdMemberRelationship(HouseholdMemberRelationship relationship) {
        if (this.relationships == null) this.relationships = new ArrayList<>();
        relationship.setHouseholdMemberId(this.getId());
        relationship.setHouseholdMemberClientReferenceId(this.getClientReferenceId());
        this.relationships.add(relationship);
        return this;
    }
}

