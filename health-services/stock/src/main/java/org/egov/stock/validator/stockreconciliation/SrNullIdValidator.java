package org.egov.stock.validator.stockreconciliation;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.validateForNullId;
import static org.egov.stock.Constants.GET_STOCK_RECONCILIATION;

@Component
@Slf4j
@Order(1)
public class SrNullIdValidator implements Validator<StockReconciliationBulkRequest, StockReconciliation> {
    @Override
    public Map<StockReconciliation, List<Error>> validate(StockReconciliationBulkRequest request) {
        return validateForNullId(request, GET_STOCK_RECONCILIATION);
    }
}
