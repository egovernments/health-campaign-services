package org.egov.workerregistry.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.workerregistry.web.models.Worker;
import org.egov.workerregistry.web.models.WorkerBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;

@Component
@Order(2)
@Slf4j
public class WTenantIdValidator implements Validator<WorkerBulkRequest, Worker> {

    @Override
    public Map<Worker, List<Error>> validate(WorkerBulkRequest request) {
        Map<Worker, List<Error>> errorDetailsMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(request.getWorkers())) {
            request.getWorkers().stream()
                    .filter(notHavingErrors())
                    .forEach(worker -> {
                        if (!StringUtils.hasText(worker.getTenantId())) {
                            Error error = Error.builder()
                                    .errorMessage("TenantId is required")
                                    .errorCode("MISSING_TENANT_ID")
                                    .type(Error.ErrorType.NON_RECOVERABLE)
                                    .build();
                            populateErrorDetails(worker, error, errorDetailsMap);
                        }
                    });
        }
        return errorDetailsMap;
    }
}
