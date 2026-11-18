package org.egov.project.validator.facility;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.service.FacilityAccessService;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;

@Component
@Order(value = 9)
@Slf4j
public class PfAllowedFacilityValidator implements Validator<ProjectFacilityBulkRequest, ProjectFacility> {

    private static final String FACILITY_ACCESS_DENIED = "FACILITY_ACCESS_DENIED";

    private final FacilityAccessService facilityAccessService;

    public PfAllowedFacilityValidator(FacilityAccessService facilityAccessService) {
        this.facilityAccessService = facilityAccessService;
    }

    @Override
    public Map<ProjectFacility, List<Error>> validate(ProjectFacilityBulkRequest request) {
        if (!facilityAccessService.isRestricted(request.getRequestInfo())) {
            return Collections.emptyMap();
        }
        log.info("validating facility access for restricted user");
        Map<ProjectFacility, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectFacility> validEntities = request.getProjectFacilities().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList());
        if (validEntities.isEmpty()) {
            return errorDetailsMap;
        }
        Set<String> allowed = facilityAccessService.allowedFacilityIds(request.getRequestInfo(), getTenantId(validEntities));
        validEntities.stream()
                .filter(entity -> !allowed.contains(entity.getFacilityId()))
                .forEach(entity -> populateErrorDetails(entity, Error.builder()
                        .errorMessage("Facility access denied for " + entity.getFacilityId())
                        .errorCode(FACILITY_ACCESS_DENIED)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException(FACILITY_ACCESS_DENIED,
                                "Facility access denied for " + entity.getFacilityId()))
                        .build(), errorDetailsMap));
        return errorDetailsMap;
    }
}
