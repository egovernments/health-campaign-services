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

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForIsDelete;

@Component
@Order(2)
@Slf4j
public class PfIsDeletedValidator implements Validator<ProjectFacilityBulkRequest, ProjectFacility> {

    @Override
    public Map<ProjectFacility, List<Error>> validate(ProjectFacilityBulkRequest request) {
        log.info("validating isDeleted field");
        HashMap<ProjectFacility, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectFacility> validIndividuals = request.getProjectFacilities();
        validIndividuals.stream().filter(ProjectFacility::getIsDeleted).forEach(individual -> {
            Error error = getErrorForIsDelete();
            populateErrorDetails(individual, error, errorDetailsMap);
        });
        return errorDetailsMap;
    }
}
