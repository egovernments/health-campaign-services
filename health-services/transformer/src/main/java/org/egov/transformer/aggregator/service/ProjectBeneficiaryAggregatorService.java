package org.egov.transformer.aggregator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.Task;
import org.egov.transformer.aggregator.config.ServiceConfiguration;
import org.egov.transformer.aggregator.models.AggregatedProjectBeneficiary;
import org.egov.transformer.aggregator.models.ElasticsearchHit;
import org.egov.transformer.aggregator.repository.ElasticSearchRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.egov.transformer.aggregator.config.ServiceConstants.AGG_HOUSEHOLD_ID;

@Component
@Slf4j
public class ProjectBeneficiaryAggregatorService {

    private final ServiceConfiguration config;
    private final ElasticSearchRepository elasticSearchRepository;

    public ProjectBeneficiaryAggregatorService(ServiceConfiguration config, ElasticSearchRepository elasticSearchRepository) {
        this.config = config;
        this.elasticSearchRepository = elasticSearchRepository;
    }

    public void processAggProjectBeneficiary(ProjectBeneficiary projectBeneficiary) {
        //TODO check projectBeneficiary.getId or beneficiaryId
        ElasticsearchHit<AggregatedProjectBeneficiary> esHit = findOrInitAggregatedProjectBeneficiary(projectBeneficiary.getBeneficiaryId());
        BeanUtils.copyProperties(projectBeneficiary, esHit.getSource());
        log.info("PROCESS PROJECT BENEFICIARY ::: SEQ_NO ::: {}  PRIMARY_TERM ::: {}", esHit.getSeqNo(),
                esHit.getPrimaryTerm());
        updateAggregatedProjectBeneficiary(esHit);
//        elasticSearchRepository.upsertAggregatedProjectBeneficiary(config.getAggregatedProjectBeneficiaryIndex(), esHit.getSource().getBeneficiaryId(), esHit.getSource());
    }

    public void updateAggregatedProjectBeneficiary (String projectBeneficiaryId, List<Task> tasks) {
        ElasticsearchHit<AggregatedProjectBeneficiary> esHit = findOrInitAggregatedProjectBeneficiary(projectBeneficiaryId);
        esHit.getSource().setTasks(replaceOrAddAll(esHit.getSource().getTasks(), tasks));

    }

    private List<Task> replaceOrAddAll(List<Task> existingTasks,
                                                  List<Task> newTasks) {
        if (Objects.isNull(existingTasks)) {
            existingTasks = new ArrayList<>();
        }
        Map<String, Task> indexedMap = existingTasks.stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));
        newTasks.forEach(t -> indexedMap.putIfAbsent(t.getId(), t));
        return indexedMap.values().stream().toList();
    }

    private void updateAggregatedProjectBeneficiary(ElasticsearchHit<AggregatedProjectBeneficiary> esHit) {
        try {
            elasticSearchRepository.createOrUpdateDocument( esHit.getSource(),
                    config.getAggregatedProjectBeneficiaryIndex(),  esHit.getSource().getBeneficiaryId(),
                    esHit.getSeqNo(), esHit.getPrimaryTerm());
        }
        catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                log.error("Version conflict occurred while updating projectBeneficiary ::: {}", e.getMessage());
            }
            throw e;
        }
    }

    private ElasticsearchHit<AggregatedProjectBeneficiary> findOrInitAggregatedProjectBeneficiary(String projectBeneficiaryId) {
        Optional<ElasticsearchHit<AggregatedProjectBeneficiary>> hit = getAggregatedProjectBeneficiary(projectBeneficiaryId);
        return hit.orElseGet(
                () -> new ElasticsearchHit<>(0L, 0L, initAggregatedBeneficiary(projectBeneficiaryId)));
    }

    private AggregatedProjectBeneficiary initAggregatedBeneficiary(String projectBeneficiaryId) {
        AggregatedProjectBeneficiary aggregatedProjectBeneficiary = new AggregatedProjectBeneficiary();
        aggregatedProjectBeneficiary.setBeneficiaryId(projectBeneficiaryId);
        return aggregatedProjectBeneficiary;
    }
    public Optional<ElasticsearchHit<AggregatedProjectBeneficiary>> getAggregatedProjectBeneficiary(
            String projectBeneficiaryId) {
        return elasticSearchRepository.findBySearchValueAndWithSeqNo(
                projectBeneficiaryId,
                config.getAggregatedProjectBeneficiaryIndex(), AGG_HOUSEHOLD_ID,
                new TypeReference<>() {
                });
    }

}
