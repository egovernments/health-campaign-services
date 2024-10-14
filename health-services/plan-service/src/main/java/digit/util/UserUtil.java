package digit.util;

import digit.config.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.user.UserDetailResponse;
import org.egov.common.contract.user.UserSearchRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static digit.config.ServiceConstants.ERROR_WHILE_FETCHING_FROM_USER_SERVICE;

@Slf4j
@Component
public class UserUtil {

    private Configuration configs;

    private RestTemplate restTemplate;

    public UserUtil(RestTemplate restTemplate, Configuration configs) {
        this.restTemplate = restTemplate;
        this.configs = configs;
    }

    /**
     * This method fetches user details from User Service for the provided employeeID
     *
     * @param requestInfo request info from the request
     * @param employeeId  employee id provided in the request
     * @param tenantId    tenant id from the request
     */
    public UserDetailResponse fetchUserDetail(RequestInfo requestInfo, String employeeId, String tenantId) {
        StringBuilder uri = getUserServiceUri();

        UserSearchRequest userSearchReq = getSearchReq(requestInfo, employeeId, tenantId);
        UserDetailResponse userDetailResponse = new UserDetailResponse();
        try {
            userDetailResponse = restTemplate.postForObject(uri.toString(), userSearchReq, UserDetailResponse.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_USER_SERVICE, e);
        }

        log.info(userDetailResponse.getUser().toString());
        return userDetailResponse;
    }

    /**
     * This method create the uri for User service
     *
     * @return uri for user detail search
     */
    private StringBuilder getUserServiceUri() {
        StringBuilder uri = new StringBuilder();
        return uri.append(configs.getUserServiceHost()).append(configs.getUserSearchEndPoint());
    }

    /**
     * This method creates the search request body for user detail search
     *
     * @param requestInfo Request Info from the request body
     * @param employeeId  Employee id for the provided plan employee assignment request
     * @param tenantId    Tenant id from the plan employee assignment request
     * @return Search request body for user detail search
     */
    private UserSearchRequest getSearchReq(RequestInfo requestInfo, String employeeId, String tenantId) {

        UserSearchRequest userSearchRequest = new UserSearchRequest();

        userSearchRequest.setRequestInfo(requestInfo);
        userSearchRequest.setTenantId(tenantId);
        userSearchRequest.setUuid(Collections.singletonList(employeeId));

        return userSearchRequest;
    }
}
