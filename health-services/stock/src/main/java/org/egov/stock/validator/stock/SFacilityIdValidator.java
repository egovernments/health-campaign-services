package org.egov.stock.validator.stock;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.stock.service.FacilityService;
import org.egov.stock.service.ProjectStaffService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.stock.Constants.GET_FACILITY_ID;
import static org.egov.stock.Constants.STAFF;
import static org.egov.stock.util.ValidatorUtil.validateFacilityIds;


@Component
@Order(value = 7)
@Slf4j
public class SFacilityIdValidator implements Validator<StockBulkRequest, Stock> {

    private final FacilityService facilityService;

    private final ProjectStaffService projectStaffService;

    public SFacilityIdValidator(FacilityService facilityService, ProjectStaffService projectStaffService) {
        this.facilityService = facilityService;
        this.projectStaffService = projectStaffService;
    }

    @Override
    public Map<Stock, List<Error>> validate(StockBulkRequest request) {
        log.info("validating for facility id");
        Map<Stock, List<Error>> errorDetailsMap = new HashMap<>();

        Map<Boolean, List<Stock>> validEntitiesMap = request.getStock().stream()
                .filter(notHavingErrors())
                .collect(Collectors.partitioningBy(entity -> entity.getTransactingPartyType().equals(STAFF)));
        validateFacilityIds(request, errorDetailsMap, validEntitiesMap.get(Boolean.FALSE), GET_FACILITY_ID, facilityService);
        projectStaffService.validateStaffIds(request, errorDetailsMap, validEntitiesMap.get(Boolean.TRUE), GET_FACILITY_ID);
        return errorDetailsMap;
    }

}
