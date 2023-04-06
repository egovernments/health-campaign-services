package org.egov.individual.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.user.CreateUserRequest;
import org.egov.common.models.user.UserRequest;
import org.egov.common.service.UserService;
import org.egov.individual.config.IndividualProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserIntegrationService {

    private final UserService userService;

    private final IndividualProperties individualProperties;

    @Autowired
    public UserIntegrationService(UserService userService, IndividualProperties individualProperties) {
        this.userService = userService;
        this.individualProperties = individualProperties;
    }

    public Optional<UserRequest> createUser(List<Individual> validIndividuals,
                                            RequestInfo requestInfo) {
        log.info("integrating with user service");
        List<UserRequest> userRequests = validIndividuals.stream().map(individual -> IndividualMapper
                        .toUserRequest(individual,
                                individualProperties))
                .collect(Collectors.toList());
        return userRequests.stream().flatMap(userRequest -> userService.create(
                new CreateUserRequest(requestInfo,
                        userRequest)).stream()).collect(Collectors.toList()).stream().findFirst();
    }
}
