package org.egov.facility.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.facility.repository.FacilityRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getEntitiesWithMismatchedRowVersion;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForRowVersionMismatch;

@Component
@Slf4j
@Order(4)
public class FRowVersionValidator implements Validator<FacilityBulkRequest, Facility> {

    private final FacilityRepository facilityRepository;

    public FRowVersionValidator(FacilityRepository facilityRepository) {
        this.facilityRepository = facilityRepository;
    }

    @Override
    public Map<Facility, List<Error>> validate(FacilityBulkRequest request) {
        Map<Facility, List<Error>> errorDetailsMap = new HashMap<>();
        log.info("validating row version facility");
        List<Facility> validEntities = request.getFacilities().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList());
        if (!validEntities.isEmpty()) {
            Method idMethod = getIdMethod(validEntities);
            Map<String, Facility> eMap = getIdToObjMap(validEntities, idMethod);
            if (!eMap.isEmpty()) {
                List<String> entityIds = new ArrayList<>(eMap.keySet());
                List<Facility> existingEntities = facilityRepository.findById(entityIds, getIdFieldName(idMethod), false);
                List<Facility> entitiesWithMismatchedRowVersion =
                        getEntitiesWithMismatchedRowVersion(eMap, existingEntities, idMethod);
                entitiesWithMismatchedRowVersion.forEach(facility -> {
                    Error error = getErrorForRowVersionMismatch();
                    log.info("validation failed for facility row version: {} with error :{}", idMethod, error);
                    populateErrorDetails(facility, error, errorDetailsMap);
                });
            }
        }
        log.info("facility row version validation completed successfully, total errors: "+errorDetailsMap.size());
        return errorDetailsMap;
    }
}
