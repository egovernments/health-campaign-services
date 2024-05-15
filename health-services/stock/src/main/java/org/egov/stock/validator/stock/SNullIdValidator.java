package org.egov.stock.validator.stock;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.validateForNullId;
import static org.egov.stock.Constants.GET_STOCK;

@Component
@Slf4j
@Order(1)
public class SNullIdValidator implements Validator<StockBulkRequest, Stock> {
    @Override
    public Map<Stock, List<Error>> validate(StockBulkRequest request) {
        return validateForNullId(request, GET_STOCK);
    }
}
