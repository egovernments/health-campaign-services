package org.egov.stock.service;

import java.util.*;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkResponse;
import org.egov.common.models.facility.FacilitySearch;
import org.egov.common.models.facility.FacilitySearchRequest;
import org.egov.common.models.project.ProjectFacilityBulkResponse;
import org.egov.common.models.project.ProjectFacilitySearch;
import org.egov.common.models.project.ProjectFacilitySearchRequest;
import org.egov.common.models.stock.SenderReceiverType;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.TransactionType;
import org.egov.stock.config.StockConfiguration;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForEntityWithNetworkError;
import static org.egov.stock.Constants.GET_FACILITY_ID;
import static org.egov.stock.Constants.GET_REFERENCE_ID;

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

		if (CollectionUtils.isEmpty(entityIds))
			return Collections.emptyList();

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
            log.error("error while fetching facility list: {}", ExceptionUtils.getStackTrace(e));
            entities.forEach( stockEntity -> {
                Error error = getErrorForEntityWithNetworkError();
                populateErrorDetails(stockEntity, error, errorDetailsMap);
            });
            return Collections.emptyList();
        }
    }

    public <T> Map<String, List<String>> validateProjectFacilityMappings(List<T> entities,
                                                String tenantId,
                                                Map<T, List<Error>> errorDetailsMap,
                                                RequestInfo requestInfo) {

        // Get a list of project reference IDs from the entities using reflection
        List<String> projectIds = getIdList(entities, getMethod(GET_REFERENCE_ID, entities.get(0).getClass()));
        List<String> facilityIds = null;

        // Check the type of entities being processed (either StockReconciliation or Stock)
		if (entities.get(0) instanceof StockReconciliation) {

            // If the entity is StockReconciliation, extract facility IDs directly
			facilityIds = getIdList(entities, getMethod(GET_FACILITY_ID, entities.get(0).getClass()));
		} else if (entities.get(0) instanceof Stock) {

            // If the entity is Stock, manually gather facility IDs based on sender/receiver type and transaction type
			facilityIds = new ArrayList<>();
			for (T entity : entities) {

				Stock stock = (Stock) entity;

         // Add the sender ID if the sender is a warehouse and the transaction type is DISPATCHED
				if (SenderReceiverType.WAREHOUSE.equals(stock.getSenderType()) && TransactionType.DISPATCHED.equals(stock.getTransactionType())) {
					facilityIds.add(stock.getSenderId());
				}

        // Add the receiver ID if the receiver is a warehouse and the transaction type is RECEIVED
        else if (SenderReceiverType.WAREHOUSE.equals(stock.getReceiverType()) && TransactionType.RECEIVED.equals(stock.getTransactionType())) {
           facilityIds.add(stock.getReceiverId());
        }
			}
		}

        // Calculate the search limit based on the number of project and facility IDs
        Integer searchLimit = projectIds.size() * facilityIds.size();

        // Build a request object to search for project-facility mappings
        ProjectFacilitySearchRequest projectFacilitySearchRequest = ProjectFacilitySearchRequest.builder()
                .projectFacility(ProjectFacilitySearch.builder().projectId(projectIds).facilityId(facilityIds).build())
                .requestInfo(requestInfo)
                .build();

        try {
            // Call an external service to fetch project-facility mappings using the constructed request
            ProjectFacilityBulkResponse response = serviceRequestClient.fetchResult(
                    new StringBuilder(stockConfiguration.getProjectFacilityServiceHost()
                            + stockConfiguration.getProjectFacilityServiceSearchUrl()
                            + "?limit=" + searchLimit
                            + "&offset=0&tenantId=" + tenantId),
                    projectFacilitySearchRequest,
                    ProjectFacilityBulkResponse.class);

            // Group the response by project ID and collect facility IDs associated with each project
			return response.getProjectFacilities().stream()
					.collect(Collectors.groupingBy(projectFacility -> projectFacility.getProjectId(),
							Collectors.mapping(projectFacility -> projectFacility.getFacilityId(), Collectors.toList())));

        } catch (Exception e) {
            // If an exception occurs, log the error and add network error details to each entity in the errorDetailsMap
            log.error("error while fetching project facility list: {}", ExceptionUtils.getStackTrace(e));
            entities.forEach(b -> {
                Error error = getErrorForEntityWithNetworkError();
                populateErrorDetails(b, error, errorDetailsMap);
            });
            return Collections.emptyMap();
        }
    }
}
