package org.egov.stock.validator.stockreconciliation;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
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
public class SrAllowedFacilityValidator implements Validator<StockReconciliationBulkRequest, StockReconciliation> {

    private static final String ERROR_CODE = "FACILITY_ACCESS_DENIED";

    private final FacilityAccessService facilityAccessService;

    public SrAllowedFacilityValidator(FacilityAccessService facilityAccessService) {
        this.facilityAccessService = facilityAccessService;
    }

    @Override
    public Map<StockReconciliation, List<Error>> validate(StockReconciliationBulkRequest request) {
        Map<StockReconciliation, List<Error>> errorDetailsMap = new HashMap<>();
        if (!facilityAccessService.isRestricted(request.getRequestInfo())) {
            return errorDetailsMap;
        }
        List<StockReconciliation> entities = request.getStockReconciliation().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList());
        if (entities.isEmpty()) {
            return errorDetailsMap;
        }
        log.info("validating facility access for restricted user");
        Set<String> allowed = facilityAccessService.allowedFacilityIds(request.getRequestInfo(), entities.get(0).getTenantId());
        entities.stream()
                .filter(reconciliation -> !allowed.contains(reconciliation.getFacilityId()))
                .forEach(reconciliation -> {
                    Error error = Error.builder()
                            .errorMessage("User is not allowed to act on the facility")
                            .errorCode(ERROR_CODE)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException(ERROR_CODE, "User is not allowed to act on the facility"))
                            .build();
                    populateErrorDetails(reconciliation, error, errorDetailsMap);
                });
        return errorDetailsMap;
    }
}
