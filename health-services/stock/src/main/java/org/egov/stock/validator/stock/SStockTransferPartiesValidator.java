package org.egov.stock.validator.stock;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.stock.util.ValidatorUtil.validateStockTransferParties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.egov.common.models.Error;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.service.UserService;
import org.egov.common.validator.Validator;
import org.egov.stock.service.FacilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Order(value = 7)
@Slf4j
public class SStockTransferPartiesValidator implements Validator<StockBulkRequest, Stock> {

	private final FacilityService facilityService;

	private UserService userService;

	@Autowired
	public SStockTransferPartiesValidator(FacilityService facilityService, UserService userService) {
		this.facilityService = facilityService;
		this.userService = userService;
	}

	@Override
	public Map<Stock, List<Error>> validate(StockBulkRequest request) {
		log.info("validating for facility id");
		Map<Stock, List<Error>> errorDetailsMap = new HashMap<>();

		List<Stock> validEntities = request.getStock().stream().filter(notHavingErrors()).collect(Collectors.toList());

		return validateStockTransferParties(request.getRequestInfo(), errorDetailsMap, validEntities, facilityService,
				userService);
	}
}
