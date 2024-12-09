package org.egov.transformer.aggregator.service;


import static org.egov.transformer.aggregator.config.ServiceConstants.INDIVIDUAL_ID;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.egov.common.models.core.Field;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.individual.Individual;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.aggregator.config.ServiceConfiguration;
import org.egov.transformer.aggregator.repository.ElasticSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class IndividualService {

  private static final String HOUSEHOLD_KEY_ID = "householdId";
  private final ServiceConfiguration config;
  private final ElasticSearchRepository elasticSearchRepository;
  private final HouseholdMemberService householdMemberService;
  private final HouseholdAggregatorService householdAggregatorService;

  @Autowired
  public IndividualService(ServiceConfiguration serviceConfiguration,
      ElasticSearchRepository elasticSearchRepository,
      HouseholdMemberService householdMemberService,
      HouseholdAggregatorService householdAggregatorService) {
    this.config = serviceConfiguration;
    this.elasticSearchRepository = elasticSearchRepository;
    this.householdMemberService = householdMemberService;
    this.householdAggregatorService = householdAggregatorService;
  }

  public void processIndividuals(List<Individual> individuals) {
    for (Individual individual : individuals) {
      //TODO Add try catch here
      String householdId = getHouseholdId(individual);

      if (Strings.isEmpty(householdId)) {
        List<HouseholdMember> members = householdMemberService.findByIndividualId(
            individual.getId());
        householdId = members.size() == 1 ? members.get(0).getHouseholdId() : "";
      }

      if (!Strings.isEmpty(householdId)) {
        householdAggregatorService.updateAggHousehold(householdId, individual);
      } else {
        log.error("Zero or More than one HouseholdMember found for individual {}",
            individual.getId());
        throw new CustomException("DUPLICATE_HOUSEHOLD_MEMBER",
            "Zero or More than one HouseholdMember found for individual ::: " + individual.getId());
      }
    }
  }

  public List<Individual> findByIndividualId(String individualId) {
    return elasticSearchRepository.findBySearchKeyValue(individualId, config.getIndividualIndex(),
        INDIVIDUAL_ID, Individual.class);
  }

  public String getHouseholdId(Individual individual) {
    return individual.getAdditionalFields().getFields().stream()
        .filter(field -> field.getKey().equals(HOUSEHOLD_KEY_ID))
        .findFirst()
        .map(Field::getValue).orElse("");
  }

}
