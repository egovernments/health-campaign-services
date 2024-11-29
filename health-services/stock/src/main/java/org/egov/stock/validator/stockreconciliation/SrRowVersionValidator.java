package org.egov.stock.validator.stockreconciliation;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
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
        log.info("validating row version stock reconciliation");
        Method idMethod = getIdMethod(request.getStockReconciliation());
        Map<String, StockReconciliation> eMap = getIdToObjMap(request.getStockReconciliation().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()), idMethod);
        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet());
            List<StockReconciliation> existingEntities = stockReconciliationRepository.findById(entityIds, false,
                    getIdFieldName(idMethod));
            List<StockReconciliation> entitiesWithMismatchedRowVersion =
                    getEntitiesWithMismatchedRowVersion(eMap, existingEntities, idMethod);
            entitiesWithMismatchedRowVersion.forEach(individual -> {
                Error error = getErrorForRowVersionMismatch();
                populateErrorDetails(individual, error, errorDetailsMap);
            });
        }

        log.info("stock reconciliation row version validation completed successfully, total errors: "+ errorDetailsMap.size());
        return errorDetailsMap;
    }
}