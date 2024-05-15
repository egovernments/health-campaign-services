package org.egov.project.validator.facility;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkResponse;
import org.egov.common.models.facility.FacilitySearch;
import org.egov.common.models.facility.FacilitySearchRequest;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForEntityWithNetworkError;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;
import static org.egov.project.Constants.GET_FACILITY_ID;

@Component
@Order(value = 7)
@Slf4j
public class PfFacilityIdValidator implements Validator<ProjectFacilityBulkRequest, ProjectFacility> {

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectConfiguration projectConfiguration;

    public PfFacilityIdValidator(ServiceRequestClient serviceRequestClient, ProjectConfiguration projectConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.projectConfiguration = projectConfiguration;
    }

    @Override
    public Map<ProjectFacility, List<Error>> validate(ProjectFacilityBulkRequest request) {
        log.info("validating for facility id");
        Map<ProjectFacility, List<Error>> errorDetailsMap = new HashMap<>();

        List<ProjectFacility> validEntities = request.getProjectFacilities().stream()
                .filter(notHavingErrors())
                .collect(Collectors.toList());
        if (!validEntities.isEmpty()) {
            String tenantId = getTenantId(validEntities);
            Class<?> objClass = getObjClass(validEntities);
            Method idMethod = getMethod(GET_FACILITY_ID, objClass);
            Map<String, ProjectFacility> eMap = getIdToObjMap(validEntities, idMethod);

            if (!eMap.isEmpty()) {
                List<String> entityIds = new ArrayList<>(eMap.keySet());
                List<String> existingFacilityIds = validateFacilityIds(entityIds, validEntities,
                        tenantId, errorDetailsMap, request.getRequestInfo());
                List<ProjectFacility> invalidEntities = validEntities.stream().filter(notHavingErrors()).filter(entity ->
                                !existingFacilityIds.contains(entity.getFacilityId()))
                        .collect(Collectors.toList());
                invalidEntities.forEach(projectFacility -> {
                    Error error = getErrorForNonExistentRelatedEntity(projectFacility.getFacilityId());
                    populateErrorDetails(projectFacility, error, errorDetailsMap);
                });
            }
        }

        return errorDetailsMap;
    }

    private List<String> validateFacilityIds(List<String> entityIds,
                                             List<ProjectFacility> projectFacilities,
                                             String tenantId,
                                             Map<ProjectFacility, List<Error>> errorDetailsMap,
                                             RequestInfo requestInfo) {

        FacilitySearchRequest facilitySearchRequest = FacilitySearchRequest.builder()
                .facility(FacilitySearch.builder().id(entityIds).build())
                .requestInfo(requestInfo)
                .build();

        try {
            FacilityBulkResponse response = serviceRequestClient.fetchResult(
                    new StringBuilder(projectConfiguration.getFacilityServiceHost()
                            + projectConfiguration.getFacilityServiceSearchUrl()
                            + "?limit=" + projectConfiguration.getSearchApiLimit()
                            + "&offset=0&tenantId=" + tenantId),
                    facilitySearchRequest,
                    FacilityBulkResponse.class);
            return response.getFacilities().stream().map(Facility::getId).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("error while fetching facility list", e);
            projectFacilities.forEach(b -> {
                Error error = getErrorForEntityWithNetworkError();
                populateErrorDetails(b, error, errorDetailsMap);
            });
            return entityIds;
        }
    }
}
