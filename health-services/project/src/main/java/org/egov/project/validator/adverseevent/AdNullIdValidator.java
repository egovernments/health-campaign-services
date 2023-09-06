package org.egov.project.validator.adverseevent;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.adverseevent.AdverseEvent;
import org.egov.common.models.project.adverseevent.AdverseEventBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.validateForNullId;
import static org.egov.project.Constants.GET_ADVERSE_EVENTS;


@Component
@Order(value = 1)
@Slf4j
public class AdNullIdValidator implements Validator<AdverseEventBulkRequest, AdverseEvent> {
    @Override
    public Map<AdverseEvent, List<Error>> validate(AdverseEventBulkRequest request) {
        log.info("validating for null id");
        return validateForNullId(request, GET_ADVERSE_EVENTS);
    }
}
