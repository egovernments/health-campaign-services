package org.egov.stock.validator.stockreconciliation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.common.models.stock.StockReconciliationSearch;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.stock.repository.StockReconciliationRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 * Validator class for checking the existence of StockReconciliation entities with the given client reference IDs.
 * This validator checks if the provided StockReconciliation entities already exist in the database based on their client reference IDs.
 *
 * @author kanishq-egov
 */
@Component
@Order(value = 1)
@Slf4j
public class SrExistentEntityValidator implements Validator<StockReconciliationBulkRequest, StockReconciliation> {

    private final StockReconciliationRepository stockReconciliationRepository;

    /**
     * Constructor to initialize the StockReconciliationRepository dependency.
     *
     * @param stockReconciliationRepository The repository for StockReconciliation entities.
     */
    public SrExistentEntityValidator(StockReconciliationRepository stockReconciliationRepository) {
        this.stockReconciliationRepository = stockReconciliationRepository;
    }

    /**
     * Validates the existence of StockReconciliation entities with the given client reference IDs.
     *
     * @param request The bulk request containing StockReconciliation entities.
     * @return A map containing StockReconciliation entities and their associated error details.
     */
    @Override
    public Map<StockReconciliation, List<Error>> validate(StockReconciliationBulkRequest request) {
        // Map to hold StockReconciliation entities and their error details
        Map<StockReconciliation, List<Error>> errorDetailsMap = new HashMap<>();
        // Extract tenant ID from the request
        String tenantId = CommonUtils.getTenantId(request.getStockReconciliation());
        // Get the list of StockReconciliation entities from the request
        List<StockReconciliation> entities = request.getStockReconciliation();

        // Extract client reference IDs from StockReconciliation entities that do not already have errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors()) // Filter out entities with existing errors
                .map(StockReconciliation::getClientReferenceId) // Map entities to their client reference IDs
                .collect(Collectors.toList()); // Collect IDs into a list

        // Create a map for quick lookup of StockReconciliation entities by their client reference ID
        Map<String, StockReconciliation> map = entities.stream()
                .filter(entity -> StringUtils.hasText(entity.getClientReferenceId())) // Ensure client reference ID is not empty
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity)); // Collect to a map

        // Create a search object to query existing entities by client reference IDs
        StockReconciliationSearch stockReconciliationSearch = StockReconciliationSearch.builder()
                .clientReferenceId(clientReferenceIdList) // Set the client reference IDs for the search
                .build();

        // Check if the list of client reference IDs is not empty
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Try to query the repository for existing StockReconciliation entities
            // Catch the InvalidTenantIdException
            try {
                // Query the repository to find existing StockReconciliation entities with the given client reference IDs
                List<String> existingClientReferenceIds =
                        stockReconciliationRepository.validateClientReferenceIdsFromDB(tenantId, clientReferenceIdList, Boolean.TRUE);

                // For each existing client reference ID, add an error to the map for the corresponding StockReconciliation entity
                existingClientReferenceIds.forEach(clientReferenceId -> {
                    // Get a predefined error object for unique entity validation
                    Error error = getErrorForUniqueEntity();
                    // Populate error details for the individual StockReconciliation entity associated with the client reference ID
                    populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
                });
            } catch (InvalidTenantIdException exception) {
                entities.stream().forEach(stockReconciliation -> {
                    Error error  = getErrorForInvalidTenantId(tenantId, exception);
                    // Populate error details for all entities in case of tenant ID validation failure
                    populateErrorDetails(stockReconciliation, error, errorDetailsMap);
                });

            }

        }

        // Return the map containing StockReconciliation entities and their associated error details
        return errorDetailsMap;
    }
}
