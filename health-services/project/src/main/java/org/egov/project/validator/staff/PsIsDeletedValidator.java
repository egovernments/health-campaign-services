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

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForIsDelete;

@Component
@Order(2)
@Slf4j
public class PsIsDeletedValidator implements Validator<ProjectStaffBulkRequest, ProjectStaff> {

    @Override
    public Map<ProjectStaff, List<Error>> validate(ProjectStaffBulkRequest request) {
        log.info("validating isDeleted field");
        HashMap<ProjectStaff, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectStaff> validIndividuals = request.getProjectStaff();
        validIndividuals.stream().filter(ProjectStaff::getIsDeleted).forEach(individual -> {
            Error error = getErrorForIsDelete();
            populateErrorDetails(individual, error, errorDetailsMap);
        });
        return errorDetailsMap;
    }
}
