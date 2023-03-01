package org.egov.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.stock.config.StockConfiguration;
import org.egov.stock.web.models.Facility;
import org.egov.stock.web.models.FacilityBulkResponse;
import org.egov.stock.web.models.FacilitySearch;
import org.egov.stock.web.models.FacilitySearchRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForEntityWithNetworkError;

@Service
@Slf4j
public class FacilityService {

    private final StockConfiguration stockConfiguration;

    private final ServiceRequestClient serviceRequestClient;

    public FacilityService(StockConfiguration stockConfiguration, ServiceRequestClient serviceRequestClient) {
        this.stockConfiguration = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
    }

    public <T> List<String> validateFacilityIds(List<String> entityIds,
                                             List<T> entities,
                                             String tenantId,
                                             Map<T, List<Error>> errorDetailsMap,
                                             RequestInfo requestInfo) {

        FacilitySearchRequest facilitySearchRequest = FacilitySearchRequest.builder()
                .facility(FacilitySearch.builder().id(entityIds).build())
                .requestInfo(requestInfo)
                .build();

        try {
            FacilityBulkResponse response = serviceRequestClient.fetchResult(
                    new StringBuilder(stockConfiguration.getFacilityServiceHost()
                            + stockConfiguration.getFacilityServiceSearchUrl()
                            + "?limit=" + entityIds.size()
                            + "&offset=0&tenantId=" + tenantId),
                    facilitySearchRequest,
                    FacilityBulkResponse.class);
            return response.getFacilities().stream().map(Facility::getId).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("error while fetching facility list", e);
            entities.forEach(b -> {
                Error error = getErrorForEntityWithNetworkError();
                populateErrorDetails(b, error, errorDetailsMap);
            });
            return entityIds;
        }
    }
}
