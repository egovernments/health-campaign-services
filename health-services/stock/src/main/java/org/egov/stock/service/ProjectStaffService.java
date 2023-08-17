package org.egov.stock.service;

import digit.models.coremodels.UserSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.common.service.UserService;
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

    private final UserService userService;

    public ProjectStaffService(UserService userService) {
        this.userService = userService;
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
                UserSearchRequest userSearchRequest = new UserSearchRequest();
                userSearchRequest.setRequestInfo(requestInfo);
                userSearchRequest.setUuid(entityIds);
                try {
                    List<String> existingUserIds = userService.search(userSearchRequest).stream()
                            .map(entity -> entity.getUuid()).collect(Collectors.toList());
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
