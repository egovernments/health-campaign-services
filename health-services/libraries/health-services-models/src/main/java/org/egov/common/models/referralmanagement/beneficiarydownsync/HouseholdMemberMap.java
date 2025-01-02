package org.egov.common.models.referralmanagement.beneficiarydownsync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.household.Household;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdMemberMap {

    private Household household;

    private Integer numberOfMembers;


}
