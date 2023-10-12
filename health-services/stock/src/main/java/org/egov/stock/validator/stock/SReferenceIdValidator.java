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
import static org.egov.stock.Constants.GET_REFERENCE_ID;
import static org.egov.stock.Constants.PROJECT;
import static org.egov.stock.util.ValidatorUtil.validateProjectFacilityMappings;

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

        List<Stock> validEntities = request.getStock().stream()
                .filter(notHavingErrors())
                .filter(entity -> PROJECT.equals(entity.getReferenceIdType()))
                .collect(Collectors.toList());
        
        long countOfWareHouseInStock = request.getStock().stream().filter(stock -> 
        	stock.getReceiverType().equalsIgnoreCase("WAREHOUSE") || stock.getSenderType().equalsIgnoreCase("WAREHOUSE")
        ).count();
        if(countOfWareHouseInStock == 0)
        	return errorDetailsMap;
        
        return validateProjectFacilityMappings(request, errorDetailsMap, validEntities,
                GET_REFERENCE_ID, facilityService);
    }
}
