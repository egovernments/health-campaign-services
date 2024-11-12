package org.egov.stock.validator.stockreconciliation;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.stock.service.FacilityService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.stock.Constants.GET_FACILITY_ID;
import static org.egov.stock.util.ValidatorUtil.validateFacilityIds;

@Component
@Order(value = 7)
@Slf4j
public class SrFacilityIdValidator implements Validator<StockReconciliationBulkRequest, StockReconciliation> {

    private final FacilityService facilityService;

    public SrFacilityIdValidator(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @Override
    public Map<StockReconciliation, List<Error>> validate(StockReconciliationBulkRequest request) {
        log.info("validating for facility id");
        Map<StockReconciliation, List<Error>> errorDetailsMap = new HashMap<>();

        List<StockReconciliation> validEntities = request.getStockReconciliation();

        return validateFacilityIds(request, errorDetailsMap, validEntities, GET_FACILITY_ID, facilityService);
    }


}
