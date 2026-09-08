package org.egov.facility.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
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

import static org.egov.common.utils.CommonUtils.checkNonExistentEntities;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.facility.Constants.GET_ID;

@Component
@Slf4j
@Order(2)
public class FNonExistentValidator implements Validator<FacilityBulkRequest, Facility> {

    private final FacilityRepository facilityRepository;

    public FNonExistentValidator(FacilityRepository facilityRepository) {
        this.facilityRepository = facilityRepository;
    }

    /**
     * Validates the given FacilityBulkRequest and checks for non-existent facilities and invalid tenant IDs.
     * Populates a map of facilities to their respective errors if validation issues are found.
     *
     * @param request the FacilityBulkRequest containing the list of facilities to validate
     * @return a map where each key is a Facility object and the associated value is a list of
     *         Error objects describing the validation errors for that facility
     */
    @Override
    public Map<Facility, List<Error>> validate(FacilityBulkRequest request) {
        Map<Facility, List<Error>> errorDetailsMap = new HashMap<>();
        List<Facility> validEntities = request.getFacilities().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList());
        String tenantId = getTenantId(validEntities);
        log.info("validating non existent facility");
        if (!validEntities.isEmpty()) {
            Class<?> objClass = getObjClass(validEntities);
            Method idMethod = getMethod(GET_ID, objClass);
            Map<String, Facility> eMap = getIdToObjMap(validEntities, idMethod);
            if (!eMap.isEmpty()) {
                List<String> entityIds = new ArrayList<>(eMap.keySet());
                List<Facility> existingEntities = null;
                try {
                    existingEntities = facilityRepository.findById(tenantId, entityIds, getIdFieldName(idMethod), false).getResponse();
                    List<Facility> nonExistentEntities = checkNonExistentEntities(eMap,
                            existingEntities, idMethod);
                    nonExistentEntities.forEach(facility -> {
                        Error error = getErrorForNonExistentEntity();
                        populateErrorDetails(facility, error, errorDetailsMap);
                    });
                } catch (InvalidTenantIdException exception) {
                    // Populating InvalidTenantIdException for all entities
                    validEntities.forEach(facility -> {
                        Error error = getErrorForInvalidTenantId(tenantId, exception);
                        log.error("validation failed for facility tenantId: {} with error :{}", facility.getTenantId(), error);
                        populateErrorDetails(facility, error, errorDetailsMap);
                    });
                }

            }
        }
        log.info("facility non existent validation completed successfully, total errors: "+errorDetailsMap.size());
        return errorDetailsMap;
    }
}
