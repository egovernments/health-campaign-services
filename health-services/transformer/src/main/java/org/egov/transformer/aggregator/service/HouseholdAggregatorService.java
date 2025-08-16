package org.egov.transformer.aggregator.service;



import static org.egov.transformer.aggregator.config.ServiceConstants.AGG_HOUSEHOLD_ID;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.individual.Individual;
import org.egov.transformer.aggregator.config.ServiceConfiguration;
import org.egov.transformer.aggregator.models.AggregatedHousehold;
import org.egov.transformer.aggregator.models.ElasticsearchHit;
import org.egov.transformer.aggregator.repository.ElasticSearchRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
@Slf4j
public class HouseholdAggregatorService {

  private final ServiceConfiguration config;
  private final ElasticSearchRepository elasticSearchRepository;

  @Autowired
  public HouseholdAggregatorService(ServiceConfiguration serviceConfiguration,
      ElasticSearchRepository elasticSearchRepository) {
    this.config = serviceConfiguration;
    this.elasticSearchRepository = elasticSearchRepository;
  }

  public void processAggHouseholds(Household household) {
    ElasticsearchHit<AggregatedHousehold> esHit = findOrInitAggregatedHousehold(
        household.getId());
    BeanUtils.copyProperties(household, esHit.getSource());
    log.info("PROCESS HOUSEHOLD ::: SEQ_NO ::: {}  PRIMARY_TERM ::: {}", esHit.getSeqNo(),
        esHit.getPrimaryTerm());
    updateAggregatedHousehold(esHit);
    elasticSearchRepository.upsertAggregatedHousehold(config.getAggregatedHouseholdIndex(), esHit.getSource().getId(), esHit.getSource());
  }


  public void updateAggHousehold(String householdId, List<HouseholdMember> householdMembers) {
    ElasticsearchHit<AggregatedHousehold> esHit = findOrInitAggregatedHousehold(householdId);

    esHit.getSource().setHouseholdMembers(
        replaceOrAddAll(esHit.getSource().getHouseholdMembers(), householdMembers));

    log.info("PROCESS HOUSEHOLD MEMBERS : SEQ_NO :: {}  PRIMARY_TERM :: {}", esHit.getSeqNo(),
        esHit.getPrimaryTerm());
    elasticSearchRepository.upsertAggregatedHousehold(config.getAggregatedHouseholdIndex(), esHit.getSource().getId(), esHit.getSource());
  }

  public void updateAggHousehold(String householdId, Individual individual) {
    ElasticsearchHit<AggregatedHousehold> esHit = findOrInitAggregatedHousehold(householdId);
    esHit.getSource().setIndividuals(
        replaceOrAddIndividual(esHit.getSource().getIndividuals(), individual));

    log.info("PROCESS INDIVIDUALS ::: SEQ_NO ::: {}  PRIMARY_TERM ::: {}", esHit.getSeqNo(),
        esHit.getPrimaryTerm());


    elasticSearchRepository.upsertAggregatedHousehold(config.getAggregatedHouseholdIndex(), esHit.getSource().getId(), esHit.getSource());
  }


  public Optional<ElasticsearchHit<AggregatedHousehold>> getAggregatedHousehold(
      String householdId) {
    return elasticSearchRepository.findBySearchValueAndWithSeqNo(
        householdId,
        config.getAggregatedHouseholdIndex(), AGG_HOUSEHOLD_ID,
        new TypeReference<>() {
        });
  }


  public void updateAggregatedHousehold(ElasticsearchHit<AggregatedHousehold> esHit) {
    try {
      elasticSearchRepository.createOrUpdateDocument(esHit.getSource(),
          config.getAggregatedHouseholdIndex(), esHit.getSource().getId(), esHit.getSeqNo(),
          esHit.getPrimaryTerm());
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.CONFLICT) {
        log.error("Version conflict occurred while updating household ::: {}", e.getMessage());
      }
      throw e;
    }
  }

  private ElasticsearchHit<AggregatedHousehold> findOrInitAggregatedHousehold(String householdId) {
    Optional<ElasticsearchHit<AggregatedHousehold>> hit = getAggregatedHousehold(householdId);
    return hit.orElseGet(
        () -> new ElasticsearchHit<>(0L, 0L, initAggregatedHousehold(householdId)));
  }

  private AggregatedHousehold initAggregatedHousehold(String householdId) {
    AggregatedHousehold aggregatedHousehold = new AggregatedHousehold();
    aggregatedHousehold.setHouseholdMembers(new ArrayList<>());
    aggregatedHousehold.setIndividuals(new ArrayList<>());
    aggregatedHousehold.setId(householdId);
    return aggregatedHousehold;
  }

  private List<HouseholdMember> replaceOrAddAll(List<HouseholdMember> existingHouseholdMembers,
      List<HouseholdMember> newHouseholdMembers) {
    if (Objects.isNull(existingHouseholdMembers)) {
      existingHouseholdMembers = new ArrayList<>();
    }

    Map<String, HouseholdMember> indexedMap = existingHouseholdMembers.stream()
        .collect(Collectors.toMap(HouseholdMember::getId, Function.identity()));
    newHouseholdMembers.forEach(newHm -> indexedMap.put(newHm.getId(), newHm));
    return indexedMap.values().stream().toList();
  }

  private List<Individual> replaceOrAddIndividual(List<Individual> individuals,
      Individual individualForIndex) {
    if (Objects.isNull(individuals)) {
      individuals = new ArrayList<>();
    }

    for (int i = 0; i < individuals.size(); i++) {
      if (Objects.equals(individuals.get(i).getId(), individualForIndex.getId())) {
        individuals.set(i, individualForIndex);
        return individuals;
      }
    }
    individuals.add(individualForIndex);
    return individuals;
  }
}
