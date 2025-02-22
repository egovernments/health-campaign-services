package org.egov.transformer.aggregator.service;


import static org.egov.transformer.aggregator.config.ServiceConstants.HOUSEHOLD_ID;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.Household;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.aggregator.config.ServiceConfiguration;
import org.egov.transformer.aggregator.repository.ElasticSearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HouseholdService {

  private final ServiceConfiguration config;
  private final ElasticSearchRepository elasticSearchRepository;
  private final HouseholdAggregatorService householdAggregatorService;

  @Autowired
  protected HouseholdService(
      ServiceConfiguration serviceConfiguration, ElasticSearchRepository elasticSearchRepository,
      HouseholdAggregatorService householdAggregatorService
  ) {
    this.config = serviceConfiguration;
    this.elasticSearchRepository = elasticSearchRepository;
    this.householdAggregatorService = householdAggregatorService;
  }

  public void processHouseholds(List<Household> households) {
    for (Household household : households) {
      householdAggregatorService.processAggHouseholds(household);
    }
  }

  public List<Household> getHouseholdById(String householdId) {
    try {
      return elasticSearchRepository.findBySearchKeyValue(householdId, config.getHouseholdIndex(),
          HOUSEHOLD_ID, Household.class);
    } catch (Exception ex) {
      log.error(ex.getMessage(), ex);
      throw new CustomException("DATABASE_ERROR", ex.getMessage());
    }
  }
}
