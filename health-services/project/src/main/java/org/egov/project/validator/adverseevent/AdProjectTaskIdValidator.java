package org.egov.project.validator.adverseevent;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.AdverseEvent;
import org.egov.common.models.project.AdverseEventBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;


@Component
@Order(value = 3)
@Slf4j
public class AdProjectTaskIdValidator implements Validator<AdverseEventBulkRequest, AdverseEvent> {
    private final ProjectTaskRepository projectTaskRepository;

    @Autowired
    public AdProjectTaskIdValidator(ProjectTaskRepository projectTaskRepository) {
        this.projectTaskRepository = projectTaskRepository;
    }


    @Override
    public Map<AdverseEvent, List<Error>> validate(AdverseEventBulkRequest request) {
        log.info("validating project task id");
        Map<AdverseEvent, List<Error>> errorDetailsMap = new HashMap<>();
        List<AdverseEvent> entities = request.getAdverseEvents();
        Class<?> objClass = getObjClass(entities);
        Method idMethod = getMethod("getTaskId", objClass);
        Map<String, AdverseEvent> eMap = getIdToObjMap(entities
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet());
            List<String> existingProjectTaskIds = projectTaskRepository.findById(entityIds, getIdFieldName(idMethod),Boolean.FALSE)
                    .stream().map(t -> t.getId())
                    .collect(Collectors.toList());
            List<AdverseEvent> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                            !existingProjectTaskIds.contains(entity.getTaskId()))
                    .collect(Collectors.toList());
            invalidEntities.forEach(adverseEvent -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(adverseEvent, error, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }
}
