package org.egov.facility.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.facility.web.models.Facility;
import org.egov.facility.web.models.FacilityBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForIsDelete;

@Component
@Slf4j
@Order(5)
public class FIsDeletedValidator implements Validator<FacilityBulkRequest, Facility> {
    @Override
    public Map<Facility, List<Error>> validate(FacilityBulkRequest request) {
        HashMap<Facility, List<Error>> errorDetailsMap = new HashMap<>();
        log.info("validating is deleted facility");
        List<Facility> validEntities = request.getFacilities().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        validEntities.stream().filter(Facility::getIsDeleted).forEach(facility -> {
            Error error = getErrorForIsDelete();
            log.info("validation failed for facility is deleted: {} with error: {}", validEntities, error);
            populateErrorDetails(facility, error, errorDetailsMap);
        });
        log.info("facility is deleted validation completed successfully, total errors: "+errorDetailsMap.size());
        return errorDetailsMap;
    }
}
