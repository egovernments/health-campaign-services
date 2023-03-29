package org.egov.stock.validator.stock;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.stock.service.FacilityService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.stock.Constants.GET_TRANSACTING_PARTY_ID;
import static org.egov.stock.Constants.WAREHOUSE;
import static org.egov.stock.util.ValidatorUtil.validateFacilityIds;

@Component
@Order(value = 9)
@Slf4j
public class STransactingPartyIdValidator implements Validator<StockBulkRequest, Stock> {

    private final FacilityService facilityService;

    public STransactingPartyIdValidator(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @Override
    public Map<Stock, List<Error>> validate(StockBulkRequest request) {
        log.info("validating for reference id");
        Map<Stock, List<Error>> errorDetailsMap = new HashMap<>();

        List<Stock> validFacilityTransactingPartyIdEntities = request.getStock().stream()
                .filter(notHavingErrors())
                .filter(entity -> WAREHOUSE.equals(entity.getTransactingPartyType()))
                .collect(Collectors.toList());
        return validateFacilityIds(request, errorDetailsMap, validFacilityTransactingPartyIdEntities,
                GET_TRANSACTING_PARTY_ID, facilityService);
    }
}
