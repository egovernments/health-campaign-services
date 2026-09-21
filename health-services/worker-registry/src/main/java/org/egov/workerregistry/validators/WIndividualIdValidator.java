package org.egov.workerregistry.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.workerregistry.config.WorkerRegistryConfiguration;
import org.egov.workerregistry.service.IndividualServiceClient;
import org.egov.workerregistry.web.models.Worker;
import org.egov.workerregistry.web.models.WorkerBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;

@Component
@Order(2)
@Slf4j
public class WIndividualIdValidator implements Validator<WorkerBulkRequest, Worker> {

    private final IndividualServiceClient individualServiceClient;
    private final WorkerRegistryConfiguration config;

    public WIndividualIdValidator(IndividualServiceClient individualServiceClient,
                                  WorkerRegistryConfiguration config) {
        this.individualServiceClient = individualServiceClient;
        this.config = config;
    }

    @Override
    public Map<Worker, List<Error>> validate(WorkerBulkRequest request) {
        Map<Worker, List<Error>> errorDetailsMap = new HashMap<>();

        if (!config.getIndividualValidationEnabled()) {
            log.info("Individual ID validation is disabled, skipping");
            return errorDetailsMap;
        }

        if (CollectionUtils.isEmpty(request.getWorkers())) {
            return errorDetailsMap;
        }

        List<Worker> validWorkers = request.getWorkers().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList());

        List<String> allIndividualIds = validWorkers.stream()
                .filter(w -> !CollectionUtils.isEmpty(w.getIndividualIds()))
                .flatMap(w -> w.getIndividualIds().stream())
                .distinct()
                .collect(Collectors.toList());

        if (allIndividualIds.isEmpty()) {
            return errorDetailsMap;
        }

        String tenantId = validWorkers.get(0).getTenantId();
        log.info("Validating {} individual IDs for {} workers", allIndividualIds.size(), validWorkers.size());

        Set<String> existingIds;
        try {
            existingIds = individualServiceClient.validateIndividualIds(
                    allIndividualIds, tenantId, request.getRequestInfo());
        } catch (Exception e) {
            log.error("Error validating individual IDs against individual service", e);
            Error error = Error.builder()
                    .errorMessage("Unable to validate individuals - service unavailable")
                    .errorCode("INDIVIDUAL_SERVICE_UNAVAILABLE")
                    .type(Error.ErrorType.NON_RECOVERABLE)
                    .build();
            validWorkers.stream()
                    .filter(w -> !CollectionUtils.isEmpty(w.getIndividualIds()))
                    .forEach(worker -> populateErrorDetails(worker, error, errorDetailsMap));
            return errorDetailsMap;
        }

        log.info("Individual validation result: {} found out of {} requested",
                existingIds.size(), allIndividualIds.size());

        for (Worker worker : validWorkers) {
            if (CollectionUtils.isEmpty(worker.getIndividualIds())) {
                continue;
            }
            for (String individualId : worker.getIndividualIds()) {
                if (!existingIds.contains(individualId)) {
                    Error error = Error.builder()
                            .errorMessage("Individual with ID: " + individualId + " not found")
                            .errorCode("INDIVIDUAL_NOT_FOUND")
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .build();
                    populateErrorDetails(worker, error, errorDetailsMap);
                }
            }
        }

        return errorDetailsMap;
    }
}
