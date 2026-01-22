package org.egov.transformer.aggregator.service;


import static org.egov.transformer.aggregator.config.ServiceConstants.HOUSEHOLD_MEMBER_HOUSEHOLD_ID;
import static org.egov.transformer.aggregator.config.ServiceConstants.HOUSEHOLD_MEMBER_INDIVIDUAL_ID;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.HouseholdMember;
import org.egov.transformer.aggregator.config.ServiceConfiguration;
import org.egov.transformer.aggregator.repository.ElasticSearchRepository;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HouseholdMemberService {

  private final ServiceConfiguration config;
  private final ElasticSearchRepository elasticSearchRepository;
  private final HouseholdAggregatorService householdAggregatorService;

  public HouseholdMemberService(ServiceConfiguration config,
      ElasticSearchRepository elasticSearchRepository,
      HouseholdAggregatorService householdAggregatorService) {
    this.config = config;
    this.elasticSearchRepository = elasticSearchRepository;
    this.householdAggregatorService = householdAggregatorService;
  }

  public void processHouseholdMembers(List<HouseholdMember> householdMembers) {
    Map<String, List<HouseholdMember>> groupedByHouseholdId = householdMembers.stream()
        .collect(Collectors.groupingBy(HouseholdMember::getHouseholdId));

    for (Entry<String, List<HouseholdMember>> householdEntry : groupedByHouseholdId.entrySet()) {
      householdAggregatorService.updateAggHousehold(householdEntry.getKey(), householdEntry.getValue());
    }
  }

  public List<HouseholdMember> findByHouseholdId(String householdId) {
    return elasticSearchRepository.findBySearchKeyValue(householdId,
        config.getHouseholdMemberIndex(), HOUSEHOLD_MEMBER_HOUSEHOLD_ID, HouseholdMember.class);
  }

  public List<HouseholdMember> findByIndividualId(String individualId) {
    return elasticSearchRepository.findBySearchKeyValue(individualId,
        config.getHouseholdMemberIndex(), HOUSEHOLD_MEMBER_INDIVIDUAL_ID, HouseholdMember.class);
  }
}
