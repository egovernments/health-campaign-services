package org.egov.project.validator.facility;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.project.web.models.ProjectFacility;
import org.egov.project.web.models.ProjectFacilityBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

@Component
@Order(value = 2)
@Slf4j
public class PfUniqueEntityValidator implements Validator<ProjectFacilityBulkRequest, ProjectFacility> {

    @Override
    public Map<ProjectFacility, List<Error>> validate(ProjectFacilityBulkRequest request) {
        log.info("validating unique entity");
        Map<ProjectFacility, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectFacility> validEntities = request.getProjectFacilities()
                        .stream().filter(notHavingErrors()).collect(Collectors.toList());
        if (!validEntities.isEmpty()) {
            Map<String, ProjectFacility> eMap = getIdToObjMap(validEntities);
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
