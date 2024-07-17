package org.egov.common.models.referralmanagement.beneficiarydownsync;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.Task;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Downsync {

    @JsonProperty("Households")
	private List<Household> Households;
	
    @JsonProperty("HouseholdMembers")
	private List<HouseholdMember> HouseholdMembers;
	
    @JsonProperty("Individuals")
	private List<Individual> Individuals;
	
    @JsonProperty("ProjectBeneficiaries")
	private List<ProjectBeneficiary> ProjectBeneficiaries;

    @JsonProperty("Tasks")
	private List<Task> Tasks;
	
    @JsonProperty("SideEffects")
	private List<SideEffect> SideEffects;
	
    @JsonProperty("Referrals")
	private List<Referral> Referrals;	
    
    @JsonProperty("DownsyncCriteria")
    private DownsyncCriteria downsyncCriteria;
	
}
