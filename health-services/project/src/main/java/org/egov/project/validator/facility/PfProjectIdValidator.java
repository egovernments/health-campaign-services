package org.egov.project.validator.facility;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;
import static org.egov.project.Constants.GET_PROJECT_ID;

@Component
@Order(value = 6)
@Slf4j
public class PfProjectIdValidator implements Validator<ProjectFacilityBulkRequest, ProjectFacility> {

    private final ProjectRepository projectRepository;
    private final RedisTemplate<String,String> redisTemplate;


    @Autowired
    public PfProjectIdValidator(ProjectRepository projectRepository,RedisTemplate redisTemplate) {
        this.projectRepository = projectRepository;
        this.redisTemplate = redisTemplate;
    }


    @Override
    public Map<ProjectFacility, List<Error>> validate(ProjectFacilityBulkRequest request) {
        log.info("validating project id");
        Map<ProjectFacility, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectFacility> validEntities = request.getProjectFacilities().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList());
        if(!validEntities.isEmpty()) {
            Class<?> objClass = getObjClass(validEntities);
            Method idMethod = getMethod(GET_PROJECT_ID, objClass);
            String tenantId = getTenantId(validEntities);
            Map<String, ProjectFacility> eMap = getIdToObjMap(validEntities, idMethod);
            if (!eMap.isEmpty()) {
                List<String> entityIds = new ArrayList<>(eMap.keySet());
                try{
                List<String> existingProjectIds = new ArrayList<>();
                List<String> cacheMissIds = new ArrayList<>();

                for (String id : entityIds) {
                    String redisKey = "project-create-cache-" + id;
                    try {
                        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
                            existingProjectIds.add(id);
                        } else {
                            cacheMissIds.add(id);
                        }
                    } catch (Exception ex) {
                        log.error("Redis error while checking key: {}", redisKey, ex);
                        cacheMissIds.add(id); // safely fallback to DB
                    }
                }
                if (!cacheMissIds.isEmpty()) {
                    List<String> dbValidIds = projectRepository.validateIds(tenantId, cacheMissIds,
                        getIdFieldName(idMethod));
                    existingProjectIds.addAll(dbValidIds);
                }
                    List<ProjectFacility> invalidEntities = validEntities.stream().filter(notHavingErrors()).filter(entity ->
                                !existingProjectIds.contains(entity.getProjectId()))
                        .collect(Collectors.toList());
                invalidEntities.forEach(projectFacility -> {
                    Error error = getErrorForNonExistentRelatedEntity(projectFacility.getProjectId());
                    populateErrorDetails(projectFacility, error, errorDetailsMap);
                });
            }
            catch (InvalidTenantIdException exception) {
                    // Populating InvalidTenantIdException for all entities
                    validEntities.forEach(projectFacility -> {
                        Error error = getErrorForInvalidTenantId(tenantId, exception);
                        populateErrorDetails(projectFacility, error, errorDetailsMap);
                    });
                }
        }
        return errorDetailsMap;
    }
}
