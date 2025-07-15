package org.egov.project.validator.staff;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkRequest;
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

@Component
@Order(value = 6)
@Slf4j
public class PsProjectIdValidator implements Validator<ProjectStaffBulkRequest, ProjectStaff> {

    private final ProjectRepository projectRepository;
    private final RedisTemplate<String,String> redisTemplate;

    @Autowired
    public PsProjectIdValidator(ProjectRepository projectRepository,RedisTemplate redisTemplate) {
        this.projectRepository = projectRepository;
        this.redisTemplate = redisTemplate;
    }


    @Override
    public Map<ProjectStaff, List<Error>> validate(ProjectStaffBulkRequest request) {
        log.info("validating project id");
        Map<ProjectStaff, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectStaff> entities = request.getProjectStaff();
        Class<?> objClass = getObjClass(entities);
        Method idMethod = getMethod("getProjectId", objClass);
        String tenantId = getTenantId(entities);
        Map<String, ProjectStaff> eMap = getIdToObjMap(entities
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet());
            try{
            List<String> existingProjectIds = new ArrayList<>();
            List<String> cacheMissIds = new ArrayList<>();

            for (String id : entityIds) {
                String redisKey = "project-create-cache-" + id;
                try {
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
                        existingProjectIds.add(id); // found in cache
                    } else {
                        cacheMissIds.add(id); // not in cache
                    }
                }catch (Exception ex) {
                    log.error("Redis error while checking key: {}", redisKey, ex);
                    cacheMissIds.add(id); // fallback to DB if Redis fails
                }
            }
            // Fallback to DB only for cache misses
            if (!cacheMissIds.isEmpty()) {
                List<String> dbValidIds = projectRepository.validateIds(tenantId, cacheMissIds,
                    getIdFieldName(idMethod));
                existingProjectIds.addAll(dbValidIds);
            }
            List<ProjectStaff> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                    !existingProjectIds.contains(entity.getProjectId()))
                            .collect(Collectors.toList());
            invalidEntities.forEach(ProjectStaff -> {
                Error error = getErrorForNonExistentRelatedEntity(ProjectStaff.getProjectId());
                populateErrorDetails(ProjectStaff, error, errorDetailsMap);
            });
        }
         catch (InvalidTenantIdException exception) {
                // Populating InvalidTenantIdException for all entities
                entities.forEach(projectResource -> {
                    Error error = getErrorForInvalidTenantId(tenantId, exception);
                    populateErrorDetails(projectResource, error, errorDetailsMap);
                });
            }
        }

        return errorDetailsMap;
    }
}
