package org.egov.project.task.validators;

import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.project.web.models.Task;
import org.egov.project.web.models.TaskBulkRequest;
import org.egov.project.web.models.TaskResource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForIsDeleteSubEntity;

@Component
@Order(2)
public class PtIsDeletedSubEntityValidator  implements Validator<TaskBulkRequest, Task> {

    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
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
