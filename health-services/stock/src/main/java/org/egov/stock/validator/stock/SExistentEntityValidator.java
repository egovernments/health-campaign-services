package org.egov.stock.validator.stock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.models.stock.StockSearch;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.stock.repository.StockRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
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
        // Extract tenant ID from the request
        String tenantId = CommonUtils.getTenantId(request.getStock()); // Extract tenant ID from the request
        // Get the list of Stock entities from the request
        List<Stock> entities = request.getStock();

        // Extract client reference IDs from Stock entities that do not already have errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(entity -> StringUtils.hasText(entity.getClientReferenceId())) // Ensure client reference ID is not empty
                .map(Stock::getClientReferenceId) // Map entities to their client reference IDs
                .collect(Collectors.toList()); // Collect IDs into a list

        // Create a map for quick lookup of Stock entities by their client reference ID
        Map<String, Stock> map = entities.stream()
                .filter(entity -> StringUtils.hasText(entity.getClientReferenceId())) // Ensure client reference ID is not empty
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity)); // Collect to a map

        // Create a search object to query existing entities by client reference IDs
        StockSearch stockSearch = StockSearch.builder()
                .clientReferenceId(clientReferenceIdList) // Set the client reference IDs for the search
                .build();

        // Check if the list of client reference IDs is not empty
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing Stock entities with the given client reference IDs
            // This method will throw an exception if the tenant ID is invalid
            try {
                List<String> existingClientReferenceIds = stockRepository.validateClientReferenceIdsFromDB(tenantId, clientReferenceIdList, Boolean.TRUE);
                // For each existing client reference ID, add an error to the map for the corresponding Stock entity
                existingClientReferenceIds.forEach(clientReferenceId -> {
                    // Get a predefined error object for unique entity validation
                    Error error = getErrorForUniqueEntity();
                    // Populate error details for the individual Stock entity associated with the client reference ID
                    populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException exception) {
                entities.forEach(stock -> {
                    Error error = getErrorForInvalidTenantId(tenantId, exception);
                    populateErrorDetails(stock, error, errorDetailsMap);
                });
            }
        }

        // Return the map containing Stock entities and their associated error details
        return errorDetailsMap;
    }
}
