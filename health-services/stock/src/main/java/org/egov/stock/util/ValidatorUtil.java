package org.egov.stock.util;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.stock.service.FacilityService;
import org.egov.tracer.model.CustomException;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;
import static org.egov.stock.Constants.GET_FACILITY_ID;
import static org.egov.stock.Constants.GET_REQUEST_INFO;
import static org.egov.stock.Constants.NO_PROJECT_FACILITY_MAPPING_EXISTS;
import static org.egov.stock.Constants.PIPE;

public class ValidatorUtil {

    public static<R, T> Map<T, List<Error>> validateFacilityIds(R request,
                                                                Map<T, List<Error>> errorDetailsMap,
                                                                List<T> validEntities,
                                                                String getId,
                                                                FacilityService facilityService) {
        if (!validEntities.isEmpty()) {
            String tenantId = getTenantId(validEntities);
            Class<?> objClass = getObjClass(validEntities);
            Method idMethod = getMethod(getId, objClass);
            Map<String, T> eMap = getIdToObjMap(validEntities, idMethod);
            RequestInfo requestInfo = (RequestInfo) ReflectionUtils.invokeMethod(getMethod(GET_REQUEST_INFO,
                    request.getClass()), request);

            if (!eMap.isEmpty()) {
                List<String> entityIds = new ArrayList<>(eMap.keySet());
                List<String> existingFacilityIds = facilityService.validateFacilityIds(entityIds,
                        validEntities,
                        tenantId, errorDetailsMap, requestInfo);
                List<T> invalidEntities = validEntities.stream()
                        .filter(notHavingErrors()).filter(entity ->
                                !existingFacilityIds.contains((String) ReflectionUtils.invokeMethod(idMethod, entity)))
                        .collect(Collectors.toList());
                invalidEntities.forEach(entity -> {
                    Error error = getErrorForNonExistentRelatedEntity((String) ReflectionUtils.invokeMethod(idMethod,
                            entity));
                    populateErrorDetails(entity, error, errorDetailsMap);
                });
            }
        }

        return errorDetailsMap;
    }

    public static<R,T> Map<T, List<Error>> validateProjectFacilityMappings(R request,
                                                                           Map<T, List<Error>> errorDetailsMap,
                                                                           List<T> validEntities,
                                                                           String getId,
                                                                           FacilityService facilityService) {
        if (!validEntities.isEmpty()) {
            String tenantId = getTenantId(validEntities);
            Class<?> objClass = getObjClass(validEntities);
            Method idMethod = getMethod(getId, objClass);
            RequestInfo requestInfo = (RequestInfo) ReflectionUtils.invokeMethod(getMethod(GET_REQUEST_INFO,
                    request.getClass()), request);

            List<String> existingProjectFacilityMappingIds = facilityService
                    .validateProjectFacilityMappings(validEntities, tenantId,
                            errorDetailsMap, requestInfo);
            List<T> invalidEntities = validEntities.stream()
                    .filter(notHavingErrors()).filter(entity ->
                    {
                        String comboId = (String) ReflectionUtils.invokeMethod(getMethod(GET_FACILITY_ID, objClass),
                                entity)
                                + PIPE + (String) ReflectionUtils.invokeMethod(idMethod, entity);
                        return !existingProjectFacilityMappingIds.contains(comboId);
                    })
                    .collect(Collectors.toList());
            invalidEntities.forEach(entity -> {
                String errorMessage = String.format("No mapping exists for project id: %s & facility id: %s",
                        (String) ReflectionUtils.invokeMethod(idMethod, entity),
                        (String) ReflectionUtils.invokeMethod(getMethod(GET_FACILITY_ID, objClass),
                                entity));
                Error error = Error.builder()
                        .errorMessage(errorMessage)
                        .errorCode(NO_PROJECT_FACILITY_MAPPING_EXISTS)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException(NO_PROJECT_FACILITY_MAPPING_EXISTS, errorMessage)).build();
                populateErrorDetails(entity, error, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }
}
