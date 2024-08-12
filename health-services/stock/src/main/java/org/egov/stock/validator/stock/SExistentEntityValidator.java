package org.egov.stock.validator.stock;

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
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 * Validator class for checking the existence of Stock entities with the given client reference IDs.
 * This validator checks if the provided Stock entities already exist in the database based on their client reference IDs.
 *
 * @author kanishq-egov
 */
@Component
@Order(value = 1)
@Slf4j
public class SExistentEntityValidator implements Validator<StockBulkRequest, Stock> {

    private final StockRepository stockRepository;

    /**
     * Constructor to initialize the StockRepository dependency.
     *
     * @param stockRepository The repository for Stock entities.
     */
    public SExistentEntityValidator(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    /**
     * Validates the existence of Stock entities with the given client reference IDs.
     *
     * @param request The bulk request containing Stock entities.
     * @return A map containing Stock entities and their associated error details.
     */
    @Override
    public Map<Stock, List<Error>> validate(StockBulkRequest request) {
        // Map to hold Stock entities and their error details
        Map<Stock, List<Error>> errorDetailsMap = new HashMap<>();
        // Get the list of Stock entities from the request
        List<Stock> entities = request.getStock();
        // Extract client reference IDs from Stock entities without errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors())
                .map(Stock::getClientReferenceId)
                .collect(Collectors.toList());
        Map<String, Stock> map = entities.stream()
                .filter(entity -> StringUtils.hasText(entity.getClientReferenceId()))
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity));
        // Create a search object for querying entities by client reference IDs
        StockSearch stockSearch = StockSearch.builder()
                .clientReferenceId(clientReferenceIdList)
                .build();
        // Check if the client reference ID list is not empty
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities by client reference IDs
            List<String> existingClientReferenceIds = stockRepository.validateClientReferenceIdsFromDB(clientReferenceIdList, Boolean.TRUE);
            // For each existing entity, populate error details for uniqueness
            existingClientReferenceIds.forEach(clientReferenceId -> {
                Error error = getErrorForUniqueEntity();
                populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }
}
