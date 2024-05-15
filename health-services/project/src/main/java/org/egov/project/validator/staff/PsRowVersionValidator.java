package org.egov.project.validator.staff;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectStaffRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getEntitiesWithMismatchedRowVersion;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForRowVersionMismatch;

@Component
@Order(value = 5)
@Slf4j
public class PsRowVersionValidator implements Validator<ProjectStaffBulkRequest, ProjectStaff> {

    private final ProjectStaffRepository repository;

    @Autowired
    public PsRowVersionValidator(ProjectStaffRepository repository) {
        this.repository = repository;
    }


    @Override
    public Map<ProjectStaff, List<Error>> validate(ProjectStaffBulkRequest request) {
        log.info("validating row version");
        Map<ProjectStaff, List<Error>> errorDetailsMap = new HashMap<>();
        Method idMethod = getIdMethod(request.getProjectStaff());
        Map<String, ProjectStaff> eMap = getIdToObjMap(request.getProjectStaff().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()), idMethod);
        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet());
            List<ProjectStaff> existingEntities = repository.findById(entityIds, false,
                    getIdFieldName(idMethod));
            List<ProjectStaff> entitiesWithMismatchedRowVersion =
                    getEntitiesWithMismatchedRowVersion(eMap, existingEntities, idMethod);
            entitiesWithMismatchedRowVersion.forEach(individual -> {
                Error error = getErrorForRowVersionMismatch();
                populateErrorDetails(individual, error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }
}
