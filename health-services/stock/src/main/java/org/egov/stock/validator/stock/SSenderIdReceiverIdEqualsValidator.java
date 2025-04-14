package org.egov.stock.validator.stock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.stock.Constants.S_SENDER_RECEIVER_ID_EQUALS_VALIDATION_ERROR;

/**
 * Validator class to check if senderId and receiverId are equal in a list of Stock entities.
 */
@Component
@Order(value = 6)
@Slf4j
public class SSenderIdReceiverIdEqualsValidator implements Validator<StockBulkRequest, Stock> {

    /**
     * Validates the list of Stock entities to ensure that senderId and receiverId are not equal.
     *
     * @param stockBulkRequest The bulk request containing a list of Stock entities.
     * @return A map containing Stock entities with corresponding error details for entities with equal senderId and receiverId.
     */
    @Override
    public Map<Stock, List<Error>> validate(StockBulkRequest stockBulkRequest) {
        Map<Stock, List<Error>> errorDetailsMap = new HashMap<>();
        List<Stock> entities = stockBulkRequest.getStock();
        List<Stock> invalidEntities = new ArrayList<>();
        log.info("validating whether sender id and receiver id are same");

        // Iterate through each Stock entity in the list
        entities.forEach(stock -> {
            // Check if senderId and receiverId are equal using helper method
            if (areSenderAndReceiverEqual(stock)) {
                // If equal, add the entity to the list of invalid entities
                invalidEntities.add(stock);

                // Create an error object for the entity
                Error error = Error.builder()
                        .errorMessage("Sender Id and Receiver Id cannot be the same")
                        .errorCode(S_SENDER_RECEIVER_ID_EQUALS_VALIDATION_ERROR)
                        .type(Error.ErrorType.NON_RECOVERABLE)
                        .exception(new CustomException(S_SENDER_RECEIVER_ID_EQUALS_VALIDATION_ERROR, "Sender Id and Receiver Id cannot be the same"))
                        .build();

                // Populate error details for the entity
                populateErrorDetails(stock, error, errorDetailsMap);
            }
        });

        return errorDetailsMap;
    }

    /**
     * Helper method to check if senderId and receiverId are equal.
     *
     * @param stock The Stock entity to check.
     * @return True if senderId and receiverId are equal, false otherwise.
     */
    private boolean areSenderAndReceiverEqual(Stock stock) {
        return stock.getSenderType() == stock.getReceiverType()
                && stock.getReceiverId() != null
                && stock.getSenderId() != null
                && stock.getReceiverId().equals(stock.getSenderId());
    }
}
