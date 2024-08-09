package org.egov.stock.validator.stockreconciliation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.common.models.stock.StockReconciliationSearch;
import org.egov.common.validator.Validator;
import org.egov.stock.repository.StockReconciliationRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
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
        // Get the list of StockReconciliation entities from the request
        List<StockReconciliation> entities = request.getStockReconciliation();
        // Extract client reference IDs from StockReconciliation entities without errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors())
                .map(StockReconciliation::getClientReferenceId)
                .collect(Collectors.toList());
        Map<String, StockReconciliation> map = entities.stream()
                .filter(individual -> StringUtils.isEmpty(individual.getClientReferenceId()))
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity));
        // Create a search object for querying entities by client reference IDs
        StockReconciliationSearch stockReconciliationSearch = StockReconciliationSearch.builder()
                .clientReferenceId(clientReferenceIdList)
                .build();
        // Check if the client reference ID list is not empty
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities by client reference IDs
            List<String> existingClientReferenceIds =
                    stockReconciliationRepository.validateClientReferenceIdsFromDB(clientReferenceIdList, Boolean.TRUE);
            // For each existing entity, populate error details for uniqueness
            existingClientReferenceIds.forEach(clientReferenceId -> {
                Error error = getErrorForUniqueEntity();
                populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }
}
