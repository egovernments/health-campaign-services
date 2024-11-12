package org.egov.stock.validator.stock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.models.stock.StockSearch;
import org.egov.common.validator.Validator;
import org.egov.stock.repository.StockRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.stock.Constants.*;

@Component
@Slf4j
@Order(2)
public class SNonExistentValidator implements Validator<StockBulkRequest, Stock> {

    private final StockRepository stockRepository;

    public SNonExistentValidator(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Override
    public Map<Stock, List<Error>> validate(StockBulkRequest request) {
        Map<Stock, List<Error>> errorDetailsMap = new HashMap<>();
        List<Stock> entities = request.getStock().stream().filter(notHavingErrors()).collect(Collectors.toList());

        log.info("validating non existent stock");
        Class<?> objClass = getObjClass(entities);
        Method idMethod = getMethod(GET_ID, objClass);
        Map<String, Stock> eMap = getIdToObjMap(entities, idMethod);
        // Lists to store IDs and client reference IDs
        List<String> idList = new ArrayList<>();
        List<String> clientReferenceIdList = new ArrayList<>();
        // Extract IDs and client reference IDs from stock entities
        entities.forEach(entity -> {
            idList.add(entity.getId());
            clientReferenceIdList.add(entity.getClientReferenceId());
        });
        if (!eMap.isEmpty()) {
            StockSearch stockSearch = StockSearch.builder()
                    .clientReferenceId(clientReferenceIdList)
                    .id(idList)
                    .build();

            List<Stock> existingEntities;
            try {
                // Query the repository to find existing entities
                existingEntities = stockRepository.find(stockSearch, entities.size(), 0,
                        entities.get(0).getTenantId(), null, false);
            } catch (Exception e) {
                // Handle query builder exception
                log.error("Search failed for Stock with error: {}", e.getMessage(), e);
                throw new CustomException(S_VALIDATION_STOCK_SEARCH_FAILED, "Search Failed for Stock, " + e.getMessage());
            }
            List<Stock> nonExistentEntities = checkNonExistentEntities(eMap,
                    existingEntities, idMethod);
            nonExistentEntities.forEach(task -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(task, error, errorDetailsMap);
            });
        }

        log.info("stock non existent validation completed successfully, total errors: "+errorDetailsMap.size());
        return errorDetailsMap;
    }
}
