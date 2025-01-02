package org.egov.common.models.referralmanagement.beneficiarydownsync;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.household.Household;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownsyncCLFHousehold {

    @JsonProperty("HouseholdCountMap")
    List<HouseholdMemberMap> householdMemberCountMap;

    @JsonProperty("DownsyncCriteria")
    DownsyncCriteria downsyncCriteria;

}
