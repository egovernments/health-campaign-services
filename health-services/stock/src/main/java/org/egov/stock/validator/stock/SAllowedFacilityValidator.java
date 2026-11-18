package org.egov.stock.validator.stock;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.stock.SenderReceiverType;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.models.stock.TransactionType;
import org.egov.common.validator.Validator;
import org.egov.stock.service.FacilityAccessService;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;

@Component
@Order(value = 0)
@Slf4j
public class SAllowedFacilityValidator implements Validator<StockBulkRequest, Stock> {

    private static final String ERROR_CODE = "FACILITY_ACCESS_DENIED";

    private final FacilityAccessService facilityAccessService;

    public SAllowedFacilityValidator(FacilityAccessService facilityAccessService) {
        this.facilityAccessService = facilityAccessService;
    }

    @Override
    public Map<Stock, List<Error>> validate(StockBulkRequest request) {
        Map<Stock, List<Error>> errorDetailsMap = new HashMap<>();
        if (!facilityAccessService.isRestricted(request.getRequestInfo())) {
            return errorDetailsMap;
        }
        List<Stock> entities = request.getStock().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList());
        if (entities.isEmpty()) {
            return errorDetailsMap;
        }
        log.info("validating facility access for restricted user");
        Set<String> allowed = facilityAccessService.allowedFacilityIds(request.getRequestInfo(), entities.get(0).getTenantId());
        entities.stream()
                .filter(stock -> !isActingPartyAllowed(stock, allowed))
                .forEach(stock -> {
                    Error error = Error.builder()
                            .errorMessage("User is not allowed to act on the facility")
                            .errorCode(ERROR_CODE)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException(ERROR_CODE, "User is not allowed to act on the facility"))
                            .build();
                    populateErrorDetails(stock, error, errorDetailsMap);
                });
        return errorDetailsMap;
    }

    private boolean isActingPartyAllowed(Stock stock, Set<String> allowed) {
        if (TransactionType.DISPATCHED.equals(stock.getTransactionType())) {
            return !SenderReceiverType.WAREHOUSE.equals(stock.getSenderType()) || allowed.contains(stock.getSenderId());
        }
        if (TransactionType.RECEIVED.equals(stock.getTransactionType())) {
            return !SenderReceiverType.WAREHOUSE.equals(stock.getReceiverType()) || allowed.contains(stock.getReceiverId());
        }
        return true;
    }
}
