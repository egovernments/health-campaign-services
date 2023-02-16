package org.egov.stock.validator.stockreconciliation;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.stock.web.models.StockReconciliation;
import org.egov.stock.web.models.StockReconciliationBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForIsDelete;

@Component
@Slf4j
@Order(6)
public class SrIsDeletedValidator implements Validator<StockReconciliationBulkRequest, StockReconciliation> {
    @Override
    public Map<StockReconciliation, List<Error>> validate(StockReconciliationBulkRequest request) {
        HashMap<StockReconciliation, List<Error>> errorDetailsMap = new HashMap<>();
        List<StockReconciliation> validEntities = request.getStockReconciliation()
                .stream().filter(notHavingErrors()).collect(Collectors.toList());
        validEntities.stream().filter(StockReconciliation::getIsDeleted).forEach(individual -> {
            Error error = getErrorForIsDelete();
            populateErrorDetails(individual, error, errorDetailsMap);
        });
        return errorDetailsMap;
    }
}
