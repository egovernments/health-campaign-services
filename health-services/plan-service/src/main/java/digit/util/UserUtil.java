package digit.util;

import digit.config.Configuration;
import digit.web.models.PlanEmployeeAssignment;
import digit.web.models.PlanEmployeeAssignmentRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.user.UserDetailResponse;
import org.egov.common.contract.user.UserSearchRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static digit.config.ServiceConstants.ERROR_WHILE_FETCHING_FROM_USER_SERVICE;

@Slf4j
@Component
public class UserUtil {

    private Configuration config;

    private RestTemplate restTemplate;

    public UserUtil(RestTemplate restTemplate, Configuration config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    /**
     * This method fetches user details from User Service for the provided search request
     *
     * @param userSearchReq Search request to search for user detail response
     */
    public UserDetailResponse fetchUserDetail(UserSearchRequest userSearchReq) {

        UserDetailResponse userDetailResponse = new UserDetailResponse();

        try {
            userDetailResponse = restTemplate.postForObject(getUserServiceUri().toString(), userSearchReq, UserDetailResponse.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_USER_SERVICE, e);
        }

        return userDetailResponse;
    }

    /**
     * This method creates the uri for User service
     *
     * @return uri for user detail search
     */
    private StringBuilder getUserServiceUri() {
        return new StringBuilder().append(config.getUserServiceHost()).append(config.getUserSearchEndPoint());
    }

    /**
     * This method creates the search request body for user detail search
     *
     * @param request Plan employee assignment request for creating search request.
     * @return Search request body for user detail search
     */
    public UserSearchRequest getUserSearchReq(PlanEmployeeAssignmentRequest request) {

        PlanEmployeeAssignment planEmployeeAssignment = request.getPlanEmployeeAssignment();
        UserSearchRequest userSearchRequest = new UserSearchRequest();

        userSearchRequest.setRequestInfo(request.getRequestInfo());
        userSearchRequest.setTenantId(planEmployeeAssignment.getTenantId());
        userSearchRequest.setUuid(Collections.singletonList(planEmployeeAssignment.getEmployeeId()));

        return userSearchRequest;
    }
}
