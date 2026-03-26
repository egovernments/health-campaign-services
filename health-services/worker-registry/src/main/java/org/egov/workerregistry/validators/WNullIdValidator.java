package org.egov.workerregistry.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.workerregistry.web.models.Worker;
import org.egov.workerregistry.web.models.WorkerBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.validateForNullId;

@Component
@Order(1)
@Slf4j
public class WNullIdValidator implements Validator<WorkerBulkRequest, Worker> {

    @Override
    public Map<Worker, List<Error>> validate(WorkerBulkRequest request) {
        return validateForNullId(request, "getWorkers");
    }
}
