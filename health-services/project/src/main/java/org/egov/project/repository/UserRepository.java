package org.egov.project.repository;

import digit.models.coremodels.UserSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.project.web.models.UserServiceResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class UserRepository {

    private final RestTemplate restTemplate;

    @Value("${egov.user.host}")
    private String HOST;

    @Value("${egov.search.user.url}")
    private String SEARCH_USER_URL;

    @Autowired
    public UserRepository(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<String> validatedUserIds(List<String> userIds, String tenantId) {
        UserSearchRequest request = new UserSearchRequest();
        request.setUuid(userIds);
        request.setTenantId(tenantId);
        log.info("{} {}",HOST, SEARCH_USER_URL);
        try {
            UserServiceResponse response = restTemplate.postForObject(
                    HOST + SEARCH_USER_URL,
                    request,
                    UserServiceResponse.class);

            return new ArrayList<>(response
                            .getUsers().stream()
                            .map(User::getUuid)
                            .collect(Collectors.toList()));

        } catch (Exception e) {
            log.error("Exception while searching users : " + e.getMessage());
            throw new CustomException("EXCEPTION_IN_SEARCH_USER",  e.getMessage());
        }
    }
}
