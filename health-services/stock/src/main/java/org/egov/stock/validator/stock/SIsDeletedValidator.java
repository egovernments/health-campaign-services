package org.egov.stock.validator.stock;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
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
public class SIsDeletedValidator implements Validator<StockBulkRequest, Stock> {
    @Override
    public Map<Stock, List<Error>> validate(StockBulkRequest request) {
        HashMap<Stock, List<Error>> errorDetailsMap = new HashMap<>();
        log.info("validating is deleted stock");
        List<Stock> entities = request.getStock();
        entities.stream().filter(Stock::getIsDeleted).forEach(individual -> {
            Error error = getErrorForIsDelete();
            log.info("validation failed for stock is deleted: {} with error: {}", entities, error);
            populateErrorDetails(individual, error, errorDetailsMap);
        });
        log.info("stock is deleted validation completed successfully, total errors: "+errorDetailsMap.size());
        return errorDetailsMap;
    }
}
