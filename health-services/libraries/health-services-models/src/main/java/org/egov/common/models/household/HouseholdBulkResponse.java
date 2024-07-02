package org.egov.common.models.household;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
* HouseholdResponse
*/
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HouseholdBulkResponse {
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private ResponseInfo responseInfo = null;

    @JsonProperty("Households")
    @Valid
    private List<Household> households = null;

    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

    public HouseholdBulkResponse addHouseholdItem(Household householdItem) {
        if (this.households == null) {
            this.households = new ArrayList<>();
        }
        this.households.add(householdItem);
        return this;
    }
}

