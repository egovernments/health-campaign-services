package org.egov.workerregistry.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.workerregistry.constants.WorkerRegistryConstants;
import org.egov.workerregistry.repository.WorkerIndividualMapRepository;
import org.egov.workerregistry.web.models.WorkerIndividualMap;
import org.egov.workerregistry.web.models.WorkerIndividualMapBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

@Component
@Order(1)
@Slf4j
public class WIndividualIdUniqueValidator implements Validator<WorkerIndividualMapBulkRequest, WorkerIndividualMap> {

    private final WorkerIndividualMapRepository repository;

    public WIndividualIdUniqueValidator(WorkerIndividualMapRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<WorkerIndividualMap, List<Error>> validate(WorkerIndividualMapBulkRequest request) {
        Map<WorkerIndividualMap, List<Error>> errorDetailsMap = new HashMap<>();
        List<WorkerIndividualMap> validMaps = request.getWorkerIndividualMaps().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList());

        if (validMaps.isEmpty()) {
            return errorDetailsMap;
        }

        // Check for duplicates within the request
        List<String> individualIdsInRequest = validMaps.stream()
                .map(WorkerIndividualMap::getIndividualId)
                .collect(Collectors.toList());
        List<String> duplicatesInRequest = individualIdsInRequest.stream()
                .filter(id -> Collections.frequency(individualIdsInRequest, id) > 1)
                .distinct()
                .collect(Collectors.toList());

        if (!duplicatesInRequest.isEmpty()) {
            validMaps.stream()
                    .filter(map -> duplicatesInRequest.contains(map.getIndividualId()))
                    .forEach(map -> {
                        Error error = getErrorForUniqueEntity();
                        error.setErrorMessage("IndividualId is duplicated in the request");
                        populateErrorDetails(map, error, errorDetailsMap);
                    });
        }

        // Check for duplicates against the database
        String tenantId = getTenantId(validMaps);
        try {
            List<String> existingIndividualIds = repository.findExistingIndividualIds(individualIdsInRequest, tenantId);
            if (!existingIndividualIds.isEmpty()) {
                validMaps.stream()
                        .filter(map -> existingIndividualIds.contains(map.getIndividualId()))
                        .forEach(map -> {
                            Error error = getErrorForUniqueEntity();
                            error.setErrorMessage("IndividualId already mapped to another worker");
                            populateErrorDetails(map, error, errorDetailsMap);
                        });
            }
        } catch (InvalidTenantIdException e) {
            log.error("Invalid tenantId: {}", tenantId, e);
            validMaps.forEach(map -> {
                Error error = Error.builder()
                        .errorMessage(WorkerRegistryConstants.MSG_INVALID_TENANT_ID_PREFIX + tenantId)
                        .errorCode(WorkerRegistryConstants.INVALID_TENANT_ID)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .build();
                populateErrorDetails(map, error, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }
}