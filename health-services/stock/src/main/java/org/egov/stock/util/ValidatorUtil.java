package org.egov.stock.util;

import org.egov.common.models.Error;
import org.egov.stock.service.FacilityService;
import org.egov.stock.web.models.Stock;
import org.egov.stock.web.models.StockBulkRequest;
import org.egov.stock.web.models.StockReconciliation;
import org.egov.stock.web.models.StockReconciliationBulkRequest;
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

public class ValidatorUtil {

    public static Map<Stock, List<Error>> getStockListMap(StockBulkRequest request,
                                                   Map<Stock, List<Error>> errorDetailsMap,
                                                   List<Stock> validEntities,
                                                   String getId,
                                                   FacilityService facilityService) {
        if (!validEntities.isEmpty()) {
            String tenantId = getTenantId(validEntities);
            Class<?> objClass = getObjClass(validEntities);
            Method idMethod = getMethod(getId, objClass);
            Map<String, Stock> eMap = getIdToObjMap(validEntities, idMethod);

            if (!eMap.isEmpty()) {
                List<String> entityIds = new ArrayList<>(eMap.keySet());
                List<String> existingFacilityIds = facilityService.validateFacilityIds(entityIds,
                        validEntities,
                        tenantId, errorDetailsMap, request.getRequestInfo());
                List<Stock> invalidEntities = validEntities.stream()
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

    public static Map<StockReconciliation, List<Error>> getStockReconciliationListMap(StockReconciliationBulkRequest request,
                                                                                      Map<StockReconciliation, List<Error>> errorDetailsMap,
                                                                                      List<StockReconciliation> validEntities,
                                                                                      String getId,
                                                                                      FacilityService facilityService) {
        if (!validEntities.isEmpty()) {
            String tenantId = getTenantId(validEntities);
            Class<?> objClass = getObjClass(validEntities);
            Method idMethod = getMethod(getId, objClass);
            Map<String, StockReconciliation> eMap = getIdToObjMap(validEntities, idMethod);

            if (!eMap.isEmpty()) {
                List<String> entityIds = new ArrayList<>(eMap.keySet());
                List<String> existingFacilityIds = facilityService.validateFacilityIds(entityIds, validEntities,
                        tenantId, errorDetailsMap, request.getRequestInfo());
                List<StockReconciliation> invalidEntities = validEntities.stream()
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
}
