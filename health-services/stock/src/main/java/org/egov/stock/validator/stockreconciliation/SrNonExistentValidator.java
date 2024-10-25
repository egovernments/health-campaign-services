package org.egov.stock.validator.stockreconciliation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.common.models.stock.StockReconciliationSearch;
import org.egov.common.validator.Validator;
import org.egov.stock.repository.StockReconciliationRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.checkNonExistentEntities;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.stock.Constants.GET_ID;
import static org.egov.stock.Constants.SR_VALIDATOR_STOCK_SEARCH_FAILED;

@Component
@Slf4j
@Order(2)
public class SrNonExistentValidator implements Validator<StockReconciliationBulkRequest, StockReconciliation> {

    private final StockReconciliationRepository stockReconciliationRepository;

    public SrNonExistentValidator(StockReconciliationRepository stockReconciliationRepository) {
        this.stockReconciliationRepository = stockReconciliationRepository;
    }

    @Override
    public Map<StockReconciliation, List<Error>> validate(StockReconciliationBulkRequest request) {
        Map<StockReconciliation, List<Error>> errorDetailsMap = new HashMap<>();
        log.info("validating non existent stock reconciliation");
        List<StockReconciliation> entities = request.getStockReconciliation();
        Class<?> objClass = getObjClass(entities);
        Method idMethod = getMethod(GET_ID, objClass);
        Map<String, StockReconciliation> eMap = getIdToObjMap(entities, idMethod);
        // Lists to store IDs and client reference IDs
        List<String> idList = new ArrayList<>();
        List<String> clientReferenceIdList = new ArrayList<>();
        // Extract IDs and client reference IDs from StockReconciliation entities
        entities.forEach(entity -> {
            idList.add(entity.getId());
            clientReferenceIdList.add(entity.getClientReferenceId());
        });
        if (!eMap.isEmpty()) {
            StockReconciliationSearch stockReconciliationSearch = StockReconciliationSearch.builder()
                    .clientReferenceId(clientReferenceIdList)
                    .id(idList)
                    .build();

            List<StockReconciliation> existingEntities;
            try {
                // Query the repository to find existing entities
                existingEntities = stockReconciliationRepository.find(stockReconciliationSearch, entities.size(), 0,
                        entities.get(0).getTenantId(), null, false);
            } catch (Exception e) {
                // Handle query builder exception
                log.error("Search failed for StockReconciliation with error: {}", e.getMessage(), e);
                throw new CustomException(SR_VALIDATOR_STOCK_SEARCH_FAILED, "Search Failed for StockReconciliation, " + e.getMessage());
            }
            List<StockReconciliation> nonExistentEntities = checkNonExistentEntities(eMap,
                    existingEntities, idMethod);
            nonExistentEntities.forEach(task -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(task, error, errorDetailsMap);
            });
        }

        log.info("stock reconciliation non existent validation completed successfully, total errors: "+errorDetailsMap.size());
        return errorDetailsMap;
    }
}
