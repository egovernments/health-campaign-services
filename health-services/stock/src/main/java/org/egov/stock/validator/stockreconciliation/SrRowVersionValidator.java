package org.egov.stock.validator.stockreconciliation;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.stock.repository.StockReconciliationRepository;
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
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
import static org.egov.common.utils.ValidatorUtils.getErrorForRowVersionMismatch;

@Component
@Slf4j
@Order(4)
public class SrRowVersionValidator implements Validator<StockReconciliationBulkRequest, StockReconciliation> {

    private final StockReconciliationRepository stockReconciliationRepository;

    public SrRowVersionValidator(StockReconciliationRepository stockReconciliationRepository) {
        this.stockReconciliationRepository = stockReconciliationRepository;
    }

    @Override
    public Map<StockReconciliation, List<Error>> validate(StockReconciliationBulkRequest request) {
        Map<StockReconciliation, List<Error>> errorDetailsMap = new HashMap<>();
        String tenantId = CommonUtils.getTenantId(request.getStockReconciliation());
        log.info("validating row version stock reconciliation");
        List<StockReconciliation> entities = request.getStockReconciliation();
        Method idMethod = getIdMethod(entities);
        Map<String, StockReconciliation> eMap = getIdToObjMap(entities.stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()), idMethod);
        if (!eMap.isEmpty()) {
            try {
                List<String> entityIds = new ArrayList<>(eMap.keySet());
                List<StockReconciliation> existingEntities = stockReconciliationRepository.findById(tenantId, entityIds, false,
                        getIdFieldName(idMethod));
                List<StockReconciliation> entitiesWithMismatchedRowVersion =
                        getEntitiesWithMismatchedRowVersion(eMap, existingEntities, idMethod);
                entitiesWithMismatchedRowVersion.forEach(individual -> {
                    Error error = getErrorForRowVersionMismatch();
                    populateErrorDetails(individual, error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException exception) {
                entities.stream().forEach(stockReconciliation -> {
                    Error error = getErrorForInvalidTenantId(tenantId, exception);
                    log.info("validation failed for stock reconciliation row version: {} with error :{}", idMethod, error);
                    populateErrorDetails(stockReconciliation, error, errorDetailsMap);
                });

            }

        }

        log.info("stock reconciliation row version validation completed successfully, total errors: "+ errorDetailsMap.size());
        return errorDetailsMap;
    }
}
