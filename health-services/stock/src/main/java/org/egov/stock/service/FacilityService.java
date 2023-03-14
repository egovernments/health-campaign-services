package org.egov.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.stock.Facility;
import org.egov.common.models.stock.FacilityBulkResponse;
import org.egov.common.models.stock.FacilitySearch;
import org.egov.common.models.stock.FacilitySearchRequest;
import org.egov.common.models.stock.ProjectFacilityBulkResponse;
import org.egov.common.models.stock.ProjectFacilitySearch;
import org.egov.common.models.stock.ProjectFacilitySearchRequest;
import org.egov.stock.config.StockConfiguration;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForEntityWithNetworkError;
import static org.egov.stock.Constants.GET_FACILITY_ID;
import static org.egov.stock.Constants.GET_REFERENCE_ID;
import static org.egov.stock.Constants.PIPE;

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
            return Collections.emptyList();
        }
    }

    public <T> List<String> validateProjectFacilityMappings(List<T> entities,
                                                String tenantId,
                                                Map<T, List<Error>> errorDetailsMap,
                                                RequestInfo requestInfo) {
        List<String> projectIds = getIdList(entities, getMethod(GET_REFERENCE_ID, entities.get(0).getClass()));
        List<String> facilityIds = getIdList(entities, getMethod(GET_FACILITY_ID, entities.get(0).getClass()));
        Integer searchLimit = projectIds.size() * facilityIds.size();

        ProjectFacilitySearchRequest projectFacilitySearchRequest = ProjectFacilitySearchRequest.builder()
                .projectFacility(ProjectFacilitySearch.builder().projectId(projectIds).facilityId(facilityIds).build())
                .requestInfo(requestInfo)
                .build();

        try {
            ProjectFacilityBulkResponse response = serviceRequestClient.fetchResult(
                    new StringBuilder(stockConfiguration.getProjectFacilityServiceHost()
                            + stockConfiguration.getProjectFacilityServiceSearchUrl()
                            + "?limit=" + searchLimit
                            + "&offset=0&tenantId=" + tenantId),
                    projectFacilitySearchRequest,
                    ProjectFacilityBulkResponse.class);
            return response.getProjectFacilities().stream()
                    .map(projectFacility -> projectFacility.getFacilityId() + PIPE + projectFacility.getProjectId())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("error while fetching project facility list", e);
            entities.forEach(b -> {
                Error error = getErrorForEntityWithNetworkError();
                populateErrorDetails(b, error, errorDetailsMap);
            });
            return Collections.emptyList();
        }
    }
}
