package org.egov.project.validator.staff;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

@Component
@Order(value = 2)
@Slf4j
public class PsUniqueEntityValidator implements Validator<ProjectStaffBulkRequest, ProjectStaff> {

    @Override
    public Map<ProjectStaff, List<Error>> validate(ProjectStaffBulkRequest request) {
        log.info("validating unique entity");
        Map<ProjectStaff, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectStaff> validEntities = request.getProjectStaff()
                        .stream().filter(notHavingErrors()).collect(Collectors.toList());
        if (!validEntities.isEmpty()) {
            Map<String, ProjectStaff> eMap = getIdToObjMap(validEntities);
            if (eMap.keySet().size() != validEntities.size()) {
                List<String> duplicates = eMap.keySet().stream().filter(id ->
                        validEntities.stream()
                                .filter(entity -> entity.getId().equals(id)).count() > 1
                ).collect(Collectors.toList());
                for (String key : duplicates) {
                    Error error = getErrorForUniqueEntity();
                    populateErrorDetails(eMap.get(key), error, errorDetailsMap);
                }
            }
        }
        return errorDetailsMap;
    }
}
