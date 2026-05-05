package org.egov.workerregistry.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.workerregistry.constants.WorkerRegistryConstants;
import org.egov.workerregistry.repository.WorkerRepository;
import org.egov.workerregistry.web.models.Worker;
import org.egov.workerregistry.web.models.WorkerBulkRequest;
import org.egov.workerregistry.web.models.WorkerSearch;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getEntitiesWithMismatchedRowVersion;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForRowVersionMismatch;

@Component
@Order(3)
@Slf4j
public class WRowVersionValidator implements Validator<WorkerBulkRequest, Worker> {

    private final WorkerRepository workerRepository;

    public WRowVersionValidator(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }

    @Override
    public Map<Worker, List<Error>> validate(WorkerBulkRequest request) {
        Map<Worker, List<Error>> errorDetailsMap = new HashMap<>();
        List<Worker> entities = request.getWorkers();
        String tenantId = getTenantId(entities);

        Method idMethod = getIdMethod(entities);
        Map<String, Worker> eMap = getIdToObjMap(
                entities.stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);

        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet());
            try {
                WorkerSearch search = WorkerSearch.builder()
                        .id(entityIds)
                        .tenantId(tenantId)
                        .build();
                List<Worker> existingEntities = workerRepository.find(search);

                List<Worker> mismatchedEntities = getEntitiesWithMismatchedRowVersion(eMap,
                        existingEntities, idMethod);
                mismatchedEntities.forEach(entity -> {
                    Error error = getErrorForRowVersionMismatch();
                    populateErrorDetails(entity, error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException e) {
                log.error("Invalid tenant id: {}", tenantId, e);
                entities.forEach(worker -> {
                    Error error = Error.builder()
                            .errorMessage(WorkerRegistryConstants.MSG_INVALID_TENANT_ID_PREFIX + tenantId)
                            .errorCode(WorkerRegistryConstants.INVALID_TENANT_ID)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .build();
                    populateErrorDetails(worker, error, errorDetailsMap);
                });
            }
        }
        return errorDetailsMap;
    }
}
