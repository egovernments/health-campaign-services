package org.egov.workerregistry.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.individual.IndividualSearchRequest;
import org.egov.workerregistry.config.WorkerRegistryConfiguration;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IndividualServiceClient {

    private final ServiceRequestClient serviceRequestClient;
    private final WorkerRegistryConfiguration config;

    public IndividualServiceClient(ServiceRequestClient serviceRequestClient,
                                   WorkerRegistryConfiguration config) {
        this.serviceRequestClient = serviceRequestClient;
        this.config = config;
    }

    public Set<String> validateIndividualIds(List<String> individualIds, String tenantId,
                                             RequestInfo requestInfo) {
        if (individualIds == null || individualIds.isEmpty()) {
            return Collections.emptySet();
        }

        IndividualSearch individualSearch = IndividualSearch.builder()
                .id(individualIds)
                .build();

        IndividualSearchRequest searchRequest = IndividualSearchRequest.builder()
                .requestInfo(requestInfo)
                .individual(individualSearch)
                .build();

        StringBuilder uri = new StringBuilder(config.getIndividualServiceHost()
                + config.getIndividualServiceSearchUrl()
                + "?limit=" + config.getIndividualServiceSearchLimit()
                + "&offset=0&tenantId=" + tenantId);

        log.info("Searching individuals at: {}", uri);

        IndividualBulkResponse response = serviceRequestClient.fetchResult(
                uri, searchRequest, IndividualBulkResponse.class);

        if (response == null || response.getIndividual() == null) {
            return Collections.emptySet();
        }

        return response.getIndividual().stream()
                .map(Individual::getId)
                .collect(Collectors.toSet());
    }
}
