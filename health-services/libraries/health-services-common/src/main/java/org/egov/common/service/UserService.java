package org.egov.common.service;

import digit.models.coremodels.UserDetailResponse;
import digit.models.coremodels.UserSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@ConditionalOnExpression("!'${egov.user.integration.enabled}'.isEmpty() && ${egov.user.integration.enabled:false} && !'${egov.user.host}'.isEmpty() && !'${egov.search.user.ur}'.isEmpty()")
public class UserService {

    private final ServiceRequestClient restRepo;

    private final String host;

    private final String searchUrl;

    @Autowired
    public UserService(ServiceRequestClient restRepo,
                       @Value("${egov.user.host}") String host,
                       @Value("${egov.search.user.url}") String searchUrl) {

        this.restRepo = restRepo;
        this.host = host;
        this.searchUrl = searchUrl;
    }

    public List<User> search(UserSearchRequest userSearchRequest) {
        try {
            UserDetailResponse response =  restRepo.fetchResult(
                    new StringBuilder(host + searchUrl),
                    userSearchRequest,
                    UserDetailResponse.class
            );

            return response.getUser();

        } catch (Exception e) {
            log.error("Exception while searching users : ", e.getMessage());
            throw new CustomException("USER_SEARCH_ERROR",  e.getMessage());
        }
    }
}
