package org.egov.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkResponse;
import org.egov.common.models.project.ProjectStaffSearch;
import org.egov.common.models.project.ProjectStaffSearchRequest;
import org.egov.stock.config.StockConfiguration;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.common.utils.ValidatorUtils.getErrorForEntityWithNetworkError;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentRelatedEntity;
import static org.egov.stock.Constants.GET_REQUEST_INFO;

@Service
@Slf4j
public class ProjectStaffService {

    private final StockConfiguration stockConfiguration;

    private final ServiceRequestClient serviceRequestClient;

    public ProjectStaffService(StockConfiguration stockConfiguration, ServiceRequestClient serviceRequestClient) {
        this.stockConfiguration = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
    }

    /**
     *
     * @param request
     * @param errorDetailsMap
     * @param validEntities
     * @param getId
     * @return
     * @param <R>
     * @param <T>
     *
     *  validate the staff id using user service to check if provided uuid exists.
     */
    public <R, T> Map<T, List<Error>> validateStaffIds(R request,
                                                                Map<T, List<Error>> errorDetailsMap,
                                                                List<T> validEntities,
                                                                String getId) {
        if (!validEntities.isEmpty()) {
            String tenantId = getTenantId(validEntities);
            Class<?> objClass = getObjClass(validEntities);
            Method idMethod = getMethod(getId, objClass);
            Map<String, T> eMap = getIdToObjMap(validEntities, idMethod);
            RequestInfo requestInfo = (RequestInfo) ReflectionUtils.invokeMethod(getMethod(GET_REQUEST_INFO,
                    request.getClass()), request);

            if (!eMap.isEmpty()) {
                List<String> entityIds = new ArrayList<>(eMap.keySet());
                ProjectStaffSearchRequest projectStaffSearchRequest =
                        ProjectStaffSearchRequest.builder().requestInfo(requestInfo).projectStaff(
                                ProjectStaffSearch.builder().staffId(entityIds.stream().distinct().findFirst().get()).build()
                        ).build();
//                UserSearchRequest userSearchRequest = new UserSearchRequest();
//                userSearchRequest.setRequestInfo(requestInfo);
//                userSearchRequest.setUuid(entityIds);
                try {
//                    List<String> existingUserIds = userService.search(userSearchRequest).stream()
//                            .map(entity -> entity.getUuid()).collect(Collectors.toList());
                    ProjectStaffBulkResponse response = serviceRequestClient.fetchResult(
                            new StringBuilder(stockConfiguration.getProjectStaffServiceHost()
                                    + stockConfiguration.getProjectStaffServiceSearchUrl()
                                    + "?limit=" + entityIds.size()
                                    + "&offset=0&tenantId=" + tenantId),
                            projectStaffSearchRequest,
                            ProjectStaffBulkResponse.class);
                    List<String> existingUserIds = response.getProjectStaff().stream().map(ProjectStaff::getUserId).collect(Collectors.toList());
                    List<T> invalidEntities = validEntities.stream()
                            .filter(notHavingErrors()).filter(entity ->
                                    !existingUserIds.contains((String) ReflectionUtils.invokeMethod(idMethod, entity)))
                            .collect(Collectors.toList());
                    invalidEntities.forEach(entity -> {
                        Error error = getErrorForNonExistentRelatedEntity((String) ReflectionUtils.invokeMethod(idMethod,
                                entity));
                        populateErrorDetails(entity, error, errorDetailsMap);
                    });
                } catch (Exception e) {
                    log.error("error while fetching staff list", e);
                    validEntities.forEach(b -> {
                        Error error = getErrorForEntityWithNetworkError();
                        populateErrorDetails(b, error, errorDetailsMap);
                    });
                }
            }
        }
        return errorDetailsMap;
    }
}
