package org.egov.transformer.aggregator.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.individual.Individual;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@JsonIgnoreProperties(
    ignoreUnknown = true
)
public class AggregatedHousehold extends Household {

  @JsonProperty("householdMembers")
  private List<HouseholdMember> householdMembers;

  @JsonProperty("individuals")
  private List<Individual> individuals;
}
