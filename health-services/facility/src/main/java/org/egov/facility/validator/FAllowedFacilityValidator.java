package org.egov.facility.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.facility.service.FacilityAccessService;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;

@Component
@Slf4j
@Order(10)
public class FAllowedFacilityValidator implements Validator<FacilityBulkRequest, Facility> {

    public static final String FACILITY_ACCESS_DENIED = "FACILITY_ACCESS_DENIED";

    private final FacilityAccessService facilityAccessService;

    public FAllowedFacilityValidator(FacilityAccessService facilityAccessService) {
        this.facilityAccessService = facilityAccessService;
    }

    @Override
    public Map<Facility, List<Error>> validate(FacilityBulkRequest request) {
        Map<Facility, List<Error>> errorDetailsMap = new HashMap<>();
        if (!facilityAccessService.isRestricted(request.getRequestInfo())) {
            return errorDetailsMap;
        }
        List<Facility> entities = request.getFacilities().stream().filter(notHavingErrors()).toList();
        if (entities.isEmpty()) {
            return errorDetailsMap;
        }
        Set<String> allowed;
        try {
            allowed = facilityAccessService.allowedFacilityIds(request.getRequestInfo(), getTenantId(entities));
        } catch (Exception exception) {
            throw new CustomException(FACILITY_ACCESS_DENIED, "Unable to resolve facility access: " + exception.getMessage());
        }
        entities.stream()
                .filter(facility -> facility.getId() == null || !allowed.contains(facility.getId()))
                .forEach(facility -> populateErrorDetails(facility, Error.builder()
                        .errorMessage("User is not allowed to access this facility")
                        .errorCode(FACILITY_ACCESS_DENIED)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException(FACILITY_ACCESS_DENIED,
                                "User is not allowed to access this facility")).build(), errorDetailsMap));
        log.info("facility access validation completed, total errors: {}", errorDetailsMap.size());
        return errorDetailsMap;
    }
}
