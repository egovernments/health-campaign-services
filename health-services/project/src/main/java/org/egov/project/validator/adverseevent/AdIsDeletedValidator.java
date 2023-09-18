package org.egov.project.validator.adverseevent;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.adrm.adverseevent.AdverseEvent;
import org.egov.common.models.adrm.adverseevent.AdverseEventBulkRequest;
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
public class AdIsDeletedValidator implements Validator<AdverseEventBulkRequest, AdverseEvent> {

    @Override
    public Map<AdverseEvent, List<Error>> validate(AdverseEventBulkRequest request) {
        log.info("validating isDeleted field");
        HashMap<AdverseEvent, List<Error>> errorDetailsMap = new HashMap<>();
        List<AdverseEvent> validIndividuals = request.getAdverseEvents();
        validIndividuals.stream().filter(AdverseEvent::getIsDeleted).forEach(individual -> {
            Error error = getErrorForIsDelete();
            populateErrorDetails(individual, error, errorDetailsMap);
        });
        return errorDetailsMap;
    }
}
