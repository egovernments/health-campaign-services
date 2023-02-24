package org.egov.project.validator.facility;

import digit.models.coremodels.UserSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.models.Error;
import org.egov.common.service.UserService;
import org.egov.common.validator.Validator;
import org.egov.project.web.models.ProjectFacility;
import org.egov.project.web.models.ProjectFacilityBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.common.utils.ValidatorUtils.getErrorForEntityWithNetworkError;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;
import static org.egov.project.Constants.GET_USER_ID;

@Component
@Order(value = 7)
@Slf4j
public class PfFacilityIdValidator implements Validator<ProjectFacilityBulkRequest, ProjectFacility> {




    @Override
    public Map<ProjectFacility, List<Error>> validate(ProjectFacilityBulkRequest request) {
        log.info("validating for facility id");
        List<ProjectFacility> entities = request.getProjectFacilities();
        Map<ProjectFacility, List<Error>> errorDetailsMap = new HashMap<>();


        return errorDetailsMap;
    }
}
