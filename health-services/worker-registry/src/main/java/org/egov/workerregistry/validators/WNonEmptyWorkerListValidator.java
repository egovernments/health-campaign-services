package org.egov.workerregistry.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.workerregistry.web.models.Worker;
import org.egov.workerregistry.web.models.WorkerBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

@Component
@Order(1)
@Slf4j
public class WNonEmptyWorkerListValidator implements Validator<WorkerBulkRequest, Worker> {

    @Override
    public Map<Worker, List<Error>> validate(WorkerBulkRequest request) {
        Map<Worker, List<Error>> errorDetailsMap = new HashMap<>();
        if (CollectionUtils.isEmpty(request.getWorkers())) {
            Error error = Error.builder()
                    .errorMessage("Workers list cannot be empty")
                    .errorCode("EMPTY_WORKERS_LIST")
                    .type(Error.ErrorType.NON_RECOVERABLE)
                    .build();
            Worker dummyWorker = Worker.builder().build();
            populateErrorDetails(dummyWorker, error, errorDetailsMap);
        }
        return errorDetailsMap;
    }
}
