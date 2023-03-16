package org.egov.project.validator.staff;

import digit.models.coremodels.UserSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkRequest;
import org.egov.common.service.UserService;
import org.egov.common.validator.Validator;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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
import static org.egov.project.Constants.GET_USER_ID;

@Component
@Order(value = 7)
@Slf4j
public class PsUserIdValidator implements Validator<ProjectStaffBulkRequest, ProjectStaff> {

    private final UserService userService;

    public PsUserIdValidator(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Map<ProjectStaff, List<Error>> validate(ProjectStaffBulkRequest request) {
        log.info("validating for user id");
        List<ProjectStaff> entities = request.getProjectStaff();
        Map<ProjectStaff, List<Error>> errorDetailsMap = new HashMap<>();

        List<String> userIds = new ArrayList<>(entities.stream()
                .filter(notHavingErrors())
                .map(ProjectStaff::getUserId)
                .collect(Collectors.toSet()));
        if (!userIds.isEmpty()) {
            UserSearchRequest userSearchRequest = new UserSearchRequest();
            userSearchRequest.setTenantId(getTenantId(entities));
            userSearchRequest.setUuid(userIds);

            Map<String, ProjectStaff> uMap = getIdToObjMap(entities,
                    getMethod(GET_USER_ID, getObjClass(entities)));
            try {
                List<String> validUserIds = userService
                        .search(userSearchRequest)
                        .stream()
                        .map(User::getUuid)
                        .collect(Collectors.toList());
                for (Map.Entry<String, ProjectStaff> entry : uMap.entrySet()) {
                    if (!validUserIds.contains(entry.getKey())) {
                        ProjectStaff staff = entry.getValue();
                        Error error = getErrorForNonExistentRelatedEntity(staff.getUserId());
                        populateErrorDetails(staff, error, errorDetailsMap);
                    }
                }
            } catch (Exception exception) {
                log.error("error while validating users", exception);
                entities.stream().filter(notHavingErrors()).forEach(b -> {
                    Error error = getErrorForEntityWithNetworkError();
                    populateErrorDetails(b, error, errorDetailsMap);
                });
            }

        }
        return errorDetailsMap;
    }
}
