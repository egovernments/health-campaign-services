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
     * This method create the uri for User service
     *
     * @return uri for user detail search
     */
    private StringBuilder getUserServiceUri() {
        return new StringBuilder().append(config.getUserServiceHost()).append(config.getUserSearchEndPoint());
    }
}
