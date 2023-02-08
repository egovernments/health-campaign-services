package org.egov.stock.validator.stock;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.stock.web.models.Stock;
import org.egov.stock.web.models.StockBulkRequest;
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
public class SisDeletedValidator implements Validator<StockBulkRequest, Stock> {
    @Override
    public Map<Stock, List<Error>> validate(StockBulkRequest request) {
        HashMap<Stock, List<Error>> errorDetailsMap = new HashMap<>();
        List<Stock> validEntities = request.getStock().stream().filter(notHavingErrors()).collect(Collectors.toList());
        validEntities.stream().filter(Stock::getIsDeleted).forEach(individual -> {
            Error error = getErrorForIsDelete();
            populateErrorDetails(individual, error, errorDetailsMap);
        });
        return errorDetailsMap;
    }
}
