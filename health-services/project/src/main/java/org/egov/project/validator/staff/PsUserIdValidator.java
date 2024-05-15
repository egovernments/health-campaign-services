package org.egov.project.validator.staff;

import digit.models.coremodels.UserSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.individual.IndividualSearchRequest;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkRequest;
import org.egov.common.service.UserService;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
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

    private final ProjectConfiguration projectConfiguration;

    private final ServiceRequestClient serviceRequestClient;

    public PsUserIdValidator(UserService userService,
                             ProjectConfiguration projectConfiguration,
                             ServiceRequestClient serviceRequestClient) {
        this.userService = userService;
        this.projectConfiguration = projectConfiguration;
        this.serviceRequestClient = serviceRequestClient;
    }

    @Override
    public Map<ProjectStaff, List<Error>> validate(ProjectStaffBulkRequest request) {
        log.info("validating for user id");
        List<ProjectStaff> entities = request.getProjectStaff();
        Map<ProjectStaff, List<Error>> errorDetailsMap = new HashMap<>();

        List<String> userIds = entities.stream()
                .filter(notHavingErrors())
                .map(ProjectStaff::getUserId).distinct().collect(Collectors.toList());
        final String tenantId = getTenantId(entities);
        Map<String, ProjectStaff> uMap = getIdToObjMap(entities,
                getMethod(GET_USER_ID, getObjClass(entities)));
        if (!userIds.isEmpty()) {
            List<String> validUserIds = new ArrayList<>();
            try {
                if ("egov-user".equalsIgnoreCase(projectConfiguration.getEgovUserIdValidator())) {
                    UserSearchRequest userSearchRequest = new UserSearchRequest();
                    userSearchRequest.setTenantId(tenantId);
                    userSearchRequest.setUuid(userIds);
                    validUserIds = userService
                            .search(userSearchRequest)
                            .stream()
                            .map(User::getUuid)
                            .collect(Collectors.toList());
                } else if ("individual".equalsIgnoreCase(projectConfiguration.getEgovUserIdValidator())) {
                    IndividualSearchRequest individualSearchRequest = IndividualSearchRequest.builder()
                            .individual(IndividualSearch.builder()
                                    // assuming this is "id" field of the individual payload
                                    .id(userIds)
                                    .build())
                            .build();
                    validUserIds = serviceRequestClient.fetchResult(
                                    new StringBuilder(projectConfiguration.getIndividualServiceHost()
                                            + projectConfiguration.getIndividualServiceSearchUrl()
                                            + "?limit=" + projectConfiguration.getSearchApiLimit()
                                            + "&offset=0&tenantId=" + tenantId),
                                    individualSearchRequest,
                                    IndividualBulkResponse.class).getIndividual().stream()
                            .map(Individual::getId)
                            .collect(Collectors.toList());
                }
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
