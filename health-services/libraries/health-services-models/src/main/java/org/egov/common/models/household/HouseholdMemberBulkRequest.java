package org.egov.common.models.household;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * HouseholdMemberRequest
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HouseholdMemberBulkRequest {
    @JsonProperty("RequestInfo")
    @NotNull
    @Valid
    private org.egov.common.contract.request.RequestInfo requestInfo = null;

    @JsonProperty("HouseholdMembers")
    @NotNull
    @Valid
    @Size(min = 1)
    private List<HouseholdMember> householdMembers = new ArrayList<>();

    public HouseholdMemberBulkRequest addHouseholdMemberItem(HouseholdMember householdMemberItem) {
        this.householdMembers.add(householdMemberItem);
        return this;
    }

}

