package org.egov.facility.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.validateForNullId;
import static org.egov.facility.Constants.GET_FACILITIES;

@Component
@Slf4j
@Order(1)
public class FNullIdValidator implements Validator<FacilityBulkRequest, Facility> {
    @Override
    public Map<Facility, List<Error>> validate(FacilityBulkRequest request) {
        return validateForNullId(request, GET_FACILITIES);
    }
}
