package org.egov.common.service;

import digit.models.coremodels.UserDetailResponse;
import digit.models.coremodels.UserSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.user.CreateUserRequest;
import org.egov.common.models.user.UserRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@ConditionalOnExpression("!'${egov.user.integration.enabled}'.isEmpty() && ${egov.user.integration.enabled:false} && !'${egov.user.host}'.isEmpty() && (!'${egov.search.user.url}'.isEmpty() || !'${egov.create.user.url}'.isEmpty() || !'${egov.update.user.url}'.isEmpty())")
public class UserService {

    private final ServiceRequestClient restRepo;

    private final String host;

    private final String searchUrl;

    private final String createUrl;

    private final String updateUrl;

    @Autowired
    public UserService(ServiceRequestClient restRepo,
                       @Value("${egov.user.host}") String host,
                       @Value("${egov.search.user.url}") String searchUrl,
                       @Value("${egov.create.user.url}") String createUrl,
                       @Value("${egov.update.user.url}") String updateUrl) {

        this.restRepo = restRepo;
        this.host = host;
        this.searchUrl = searchUrl;
        this.createUrl = createUrl;
        this.updateUrl = updateUrl;
    }

    public List<User> search(UserSearchRequest userSearchRequest) {
        try {
            UserDetailResponse response = restRepo.fetchResult(
                    new StringBuilder(host + searchUrl),
                    userSearchRequest,
                    UserDetailResponse.class
            );

            return response.getUser();

        } catch (Exception e) {
            log.error("Exception while searching users : ", e);
            throw new CustomException("USER_SEARCH_ERROR", e.getMessage());
        }
    }

    public List<UserRequest> create(CreateUserRequest createUserRequest) {
        try {
            org.egov.common.models.user.UserDetailResponse response = restRepo.fetchResult(
                    new StringBuilder(host + createUrl),
                    createUserRequest,
                    org.egov.common.models.user.UserDetailResponse.class
            );
            return response.getUser();
        } catch (Exception e) {
            log.error("Exception while creating user : ", e);
            throw new CustomException("USER_CREATE_ERROR", e.getMessage());
        }
    }

    public List<UserRequest> update(CreateUserRequest createUserRequest) {
        try {
            org.egov.common.models.user.UserDetailResponse response = restRepo.fetchResult(
                    new StringBuilder(host + updateUrl),
                    createUserRequest,
                    org.egov.common.models.user.UserDetailResponse.class
            );
            return response.getUser();
        } catch (Exception e) {
            log.error("Exception while updating user : ", e);
            throw new CustomException("USER_UPDATE_ERROR", e.getMessage());
        }
    }

}