package org.egov.stock.validator.stockreconciliation;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForIsDelete;

@Component
@Slf4j
@Order(6)
public class SrIsDeletedValidator implements Validator<StockReconciliationBulkRequest, StockReconciliation> {
    @Override
    public Map<StockReconciliation, List<Error>> validate(StockReconciliationBulkRequest request) {
        HashMap<StockReconciliation, List<Error>> errorDetailsMap = new HashMap<>();
        log.info("validating is deleted stock reconciliation");
        List<StockReconciliation> entities = request.getStockReconciliation();

        entities.stream().filter(StockReconciliation::getIsDeleted).forEach(individual -> {
            Error error = getErrorForIsDelete();
            populateErrorDetails(individual, error, errorDetailsMap);
        });
        log.info("stock reconciliation is deleted validation completed successfully, total errors: "+errorDetailsMap.size());
        return errorDetailsMap;
    }
}
