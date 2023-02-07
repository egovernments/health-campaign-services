package org.egov.project.task.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.project.web.models.Task;
import org.egov.project.web.models.TaskBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.validateForNullId;
import static org.egov.project.Constants.GET_TASKS;

@Component
@Order(value = 1)
@Slf4j
public class PtNullIdValidator implements Validator<TaskBulkRequest, Task> {

    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        return validateForNullId(request, GET_TASKS);
    }
}
