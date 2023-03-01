package org.egov.stock.validator.stockreconciliation;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.stock.service.FacilityService;
import org.egov.stock.web.models.StockReconciliation;
import org.egov.stock.web.models.StockReconciliationBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
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

@Component
@Order(value = 7)
@Slf4j
public class SrFacilityIdValidator implements Validator<StockReconciliationBulkRequest, StockReconciliation> {

    private final FacilityService facilityService;

    public SrFacilityIdValidator(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @Override
    public Map<StockReconciliation, List<Error>> validate(StockReconciliationBulkRequest request) {
        log.info("validating for facility id");
        Map<StockReconciliation, List<Error>> errorDetailsMap = new HashMap<>();

        List<StockReconciliation> validEntities = request.getStockReconciliation().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList());
        if (!validEntities.isEmpty()) {
            String tenantId = getTenantId(validEntities);
            Class<?> objClass = getObjClass(validEntities);
            Method idMethod = getMethod(GET_FACILITY_ID, objClass);
            Map<String, StockReconciliation> eMap = getIdToObjMap(validEntities, idMethod);

            if (!eMap.isEmpty()) {
                List<String> entityIds = new ArrayList<>(eMap.keySet());
                List<String> existingFacilityIds = facilityService.validateFacilityIds(entityIds, validEntities,
                        tenantId, errorDetailsMap, request.getRequestInfo());
                List<StockReconciliation> invalidEntities = validEntities.stream().filter(notHavingErrors()).filter(entity ->
                                !existingFacilityIds.contains(entity.getFacilityId()))
                        .collect(Collectors.toList());
                invalidEntities.forEach(stockReconciliation -> {
                    Error error = getErrorForNonExistentRelatedEntity(stockReconciliation.getFacilityId());
                    populateErrorDetails(stockReconciliation, error, errorDetailsMap);
                });
            }
        }

        return errorDetailsMap;
    }


}
