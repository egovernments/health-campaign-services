package org.egov.project.validator.task;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskResource;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForIsDeleteSubEntity;

@Component
@Order(2)
@Slf4j
public class PtIsDeletedSubEntityValidator  implements Validator<TaskBulkRequest, Task> {

    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        log.info("validating isDeleted field for sub entity");
        HashMap<Task, List<Error>> errorDetailsMap = new HashMap<>();
        List<Task> validEntities = request.getTasks();
        for (Task entity : validEntities) {
            List<TaskResource> taskResources = entity.getResources();
            if (taskResources != null) {
                taskResources.stream().filter(TaskResource::getIsDeleted)
                        .forEach(identifier -> {
                            Error error = getErrorForIsDeleteSubEntity();
                            populateErrorDetails(entity, error, errorDetailsMap);
                        });
            }
        }
        return errorDetailsMap;
    }
}
