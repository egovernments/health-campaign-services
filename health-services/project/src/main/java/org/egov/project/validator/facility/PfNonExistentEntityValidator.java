package org.egov.project.validator.facility;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectFacilityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.checkNonExistentEntities;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.project.Constants.GET_ID;

@Component
@Order(value = 4)
@Slf4j
public class PfNonExistentEntityValidator implements Validator<ProjectFacilityBulkRequest, ProjectFacility> {

    private final ProjectFacilityRepository repository;

    @Autowired
    public PfNonExistentEntityValidator(ProjectFacilityRepository repository) {
        this.repository = repository;
    }
    
    @Override
    public Map<ProjectFacility, List<Error>> validate(ProjectFacilityBulkRequest request) {
        log.info("validating for existence of entity");
        Map<ProjectFacility, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectFacility> validEntities = request.getProjectFacilities().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList());
        if (!validEntities.isEmpty()) {
            Class<?> objClass = getObjClass(validEntities);
            Method idMethod = getMethod(GET_ID, objClass);
            Map<String, ProjectFacility> eMap = getIdToObjMap(validEntities, idMethod);
            if (!eMap.isEmpty()) {
                List<String> entityIds = new ArrayList<>(eMap.keySet());
                List<ProjectFacility> existingEntities = repository.findById(entityIds, false,
                        getIdFieldName(idMethod));
                List<ProjectFacility> nonExistentEntities = checkNonExistentEntities(eMap,
                        existingEntities, idMethod);
                nonExistentEntities.forEach(projectFacility -> {
                    Error error = getErrorForNonExistentEntity();
                    populateErrorDetails(projectFacility, error, errorDetailsMap);
                });
            }
        }
        return errorDetailsMap;
    }
}
