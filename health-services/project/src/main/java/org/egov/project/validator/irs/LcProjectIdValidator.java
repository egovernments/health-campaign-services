package org.egov.project.validator.irs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.irs.LocationCapture;
import org.egov.common.models.project.irs.LocationCaptureBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;

@Component
@Order(value = 6)
@Slf4j
public class LcProjectIdValidator implements Validator<LocationCaptureBulkRequest, LocationCapture> {
    private ProjectRepository projectRepository;

    @Autowired
    public LcProjectIdValidator(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * @param request
     * @return
     */
    @Override
    public Map<LocationCapture, List<Error>> validate(LocationCaptureBulkRequest request) {
        log.info("validating for project id");
        Map<LocationCapture, List<Error>> errorDetailsMap = new HashMap<>();
        List<LocationCapture> entities = request.getLocationCaptures();
        Class<?> objClass = getObjClass(entities);
        Method idMethod = getMethod("getProjectId", objClass);
        Map<String, LocationCapture> eMap = getIdToObjMap(entities
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet());
            List<String> existingProjectIds = projectRepository.validateIds(entityIds,
                    getIdFieldName(idMethod));
            List<LocationCapture> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                            !existingProjectIds.contains(entity.getProjectId()))
                    .collect(Collectors.toList());
            invalidEntities.forEach(locationCapture -> {
                Error error = getErrorForNonExistentRelatedEntity(locationCapture.getProjectId());
                populateErrorDetails(locationCapture, error, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }
}