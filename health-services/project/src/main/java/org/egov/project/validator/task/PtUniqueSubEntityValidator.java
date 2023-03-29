package org.egov.project.validator.task;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.Address;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.common.models.project.TaskResource;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueSubEntity;
import static org.egov.project.Constants.GET_ID;

@Component
@Order(value = 3)
@Slf4j
public class PtUniqueSubEntityValidator implements Validator<TaskBulkRequest, Task> {

    @Override
    public Map<Task, List<Error>> validate(TaskBulkRequest request) {
        log.info("validating unique sub entity");
        Map<Task, List<Error>> errorDetailsMap = new HashMap<>();
        List<Task> validEntities = request.getTasks()
                        .stream().filter(notHavingErrors()).collect(Collectors.toList());
        if (!validEntities.isEmpty()) {
            for (Task entity : validEntities) {
                if (entity.getAddress() != null) {
                    List<Address> address = Collections.singletonList(entity.getAddress());
                    Map<String, Address> eMap = getIdToObjMap(address);
                    if (eMap.keySet().size() != address.size()) {
                        List<String> duplicates = eMap.keySet().stream().filter(id ->
                                address.stream()
                                        .filter(ad -> ad.getId().equals(id)).count() > 1
                        ).collect(Collectors.toList());
                        duplicates.forEach(duplicate -> {
                            Error error = getErrorForUniqueSubEntity();
                            populateErrorDetails(entity, error, errorDetailsMap);
                        });
                    }
                }

                if (entity.getResources() != null) {
                    List<TaskResource> entities = entity.getResources().stream()
                            .filter(r -> r.getId() != null).collect(Collectors.toList());
                    if (!entities.isEmpty()) {
                        Method idMethod = getMethod(GET_ID, TaskResource.class);
                        Map<String, TaskResource> eMap = getIdToObjMap(entities, idMethod);
                        if (eMap.keySet().size() != entities.size()) {
                            List<String> duplicates = eMap.keySet().stream().filter(id ->
                                    entities.stream()
                                            .filter(idt -> idt.getId().equals(id)).count() > 1
                            ).collect(Collectors.toList());
                            duplicates.forEach( duplicate -> {
                                Error error = getErrorForUniqueSubEntity();
                                populateErrorDetails(entity, error, errorDetailsMap);
                            });
                        }
                    }
                }
            }
        }
        return errorDetailsMap;
    }
}
