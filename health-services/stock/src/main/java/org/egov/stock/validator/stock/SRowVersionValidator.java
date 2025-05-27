package org.egov.stock.validator.stock;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.stock.repository.StockRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
import static org.egov.common.utils.ValidatorUtils.getErrorForRowVersionMismatch;

@Component
@Slf4j
@Order(4)
public class SRowVersionValidator implements Validator<StockBulkRequest, Stock> {

    private final StockRepository stockRepository;

    public SRowVersionValidator(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Override
    public Map<Stock, List<Error>> validate(StockBulkRequest request) {
        Map<Stock, List<Error>> errorDetailsMap = new HashMap<>();
        String tenantId = CommonUtils.getTenantId(request.getStock());
        log.info("validating row version stock");
        Method idMethod = getIdMethod(request.getStock());
        List<Stock> entities = request.getStock();
        Map<String, Stock> eMap = getIdToObjMap(entities.stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList()), idMethod);
        if (!eMap.isEmpty()) {
            List<String> entityIds = new ArrayList<>(eMap.keySet());
            try {
                List<Stock> existingEntities = stockRepository.findById(tenantId, entityIds, false,
                        getIdFieldName(idMethod));
                List<Stock> entitiesWithMismatchedRowVersion =
                        getEntitiesWithMismatchedRowVersion(eMap, existingEntities, idMethod);
                entitiesWithMismatchedRowVersion.forEach(individual -> {
                    Error error = getErrorForRowVersionMismatch();
                    log.info("validation failed for stock row version: {} with error :{}", idMethod, error);
                    populateErrorDetails(individual, error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException exception) {
                entities.stream().forEach(stock -> {
                    Error error = getErrorForInvalidTenantId(tenantId, exception);
                    log.info("validation failed for stock row version: {} with error :{}", idMethod, error);
                    populateErrorDetails(stock, error, errorDetailsMap);
                });

            }

        }
        log.info("stock row version validation completed successfully, total errors: "+errorDetailsMap.size());
        return errorDetailsMap;
    }
}
