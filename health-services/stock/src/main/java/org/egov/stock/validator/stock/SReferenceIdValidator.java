package org.egov.stock.validator.stock;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.stock.service.FacilityService;
import org.egov.stock.web.models.Stock;
import org.egov.stock.web.models.StockBulkRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.stock.Constants.FACILITY;
import static org.egov.stock.Constants.GET_REFERENCE_ID;
import static org.egov.stock.util.ValidatorUtil.getStockListMap;

@Component
@Order(value = 8)
@Slf4j
public class SReferenceIdValidator implements Validator<StockBulkRequest, Stock> {

    private final FacilityService facilityService;

    public SReferenceIdValidator(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @Override
    public Map<Stock, List<Error>> validate(StockBulkRequest request) {
        log.info("validating for reference id");
        Map<Stock, List<Error>> errorDetailsMap = new HashMap<>();

        List<Stock> validFacilityReferenceIdEntities = request.getStock().stream()
                .filter(notHavingErrors())
                .filter(entity -> FACILITY.equals(entity.getReferenceIdType()))
                .collect(Collectors.toList());
        return getStockListMap(request, errorDetailsMap, validFacilityReferenceIdEntities, GET_REFERENCE_ID, facilityService);
    }
}
