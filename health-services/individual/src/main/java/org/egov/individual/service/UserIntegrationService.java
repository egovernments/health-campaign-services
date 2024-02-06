package org.egov.individual.service;

import digit.models.coremodels.UserSearchRequest;
import digit.models.coremodels.user.enums.UserType;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.UserDetails;
import org.egov.common.models.user.CreateUserRequest;
import org.egov.common.models.user.UserRequest;
import org.egov.common.service.UserService;
import org.egov.individual.config.IndividualProperties;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
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

    public List<UserRequest> createUser(List<Individual> validIndividuals,
                                            RequestInfo requestInfo) {
        log.info("integrating with user service");
        List<UserRequest> userRequests = validIndividuals.stream()
                .filter(Individual::getIsSystemUser).map(toUserRequest())
                .collect(Collectors.toList());
        return userRequests.stream().flatMap(userRequest -> userService.create(
                new CreateUserRequest(requestInfo,
                        userRequest)).stream()).collect(Collectors.toList());
    }


    public List<UserRequest> updateUser(List<Individual> validIndividuals,
                                            RequestInfo requestInfo) {
        log.info("updating the user in user service");
        List<UserRequest> userRequests = validIndividuals.stream()
                .filter(Individual::getIsSystemUser).map(toUserRequest())
                .collect(Collectors.toList());
        return userRequests.stream().flatMap(userRequest -> userService.update(
                new CreateUserRequest(requestInfo,
                        userRequest)).stream()).collect(Collectors.toList());
    }

    public List<UserRequest> deleteUser(List<Individual> validIndividuals,
                                            RequestInfo requestInfo) {
        log.info("deleting the user in user service");
        List<UserRequest> userRequests = validIndividuals.stream()
                .filter(Individual::getIsSystemUser).map(toUserRequest())
                .peek(userRequest -> userRequest.setActive(Boolean.FALSE))
                .collect(Collectors.toList());
        return userRequests.stream().flatMap(userRequest -> userService.update(
                new CreateUserRequest(requestInfo,
                        userRequest)).stream()).collect(Collectors.toList());
    }

    public List<User> searchUser(UserSearchRequest userSearchRequest) {
        log.info("searching in user service");
        return userService.search(userSearchRequest);
    }

    public UserSearchRequest enrichUserSearchRequest(Individual individual, RequestInfo requestInfo)
    {
        UserSearchRequest userSearchRequest = new UserSearchRequest();
        userSearchRequest.setRequestInfo(requestInfo);

        if(individual.getUserId()!=null)
            userSearchRequest.setId(Collections.singletonList(individual.getUserId()));

        if(individual.getUserUuid()!=null)
            userSearchRequest.setUuid(Collections.singletonList(individual.getUserUuid()));

        if(individual.getUserDetails()!=null && individual.getUserDetails().getUsername()!=null)
            userSearchRequest.setUserName(individual.getUserDetails().getUsername());

        if(individual.getTenantId()!=null)
            userSearchRequest.setTenantId(individual.getTenantId());

        if(individual.getUserId()!=null && individual.getUserUuid()==null )
        {
            log.error("User Uuid cannot be null while linking to HRMS Employee");
            throw new CustomException("USER_UUID_EMPTY", "User UUID cannot be null while linking to HRMS Employee");
        }
        else if(individual.getUserId()==null && individual.getUserUuid()==null && individual.getUserDetails()==null)
        {
            log.error("User Details cannot be null while linking to HRMS Employee");
            throw new CustomException("USER_DETAILS_EMPTY", "User Details cannot be null while linking to HRMS Employee");
        }
        return userSearchRequest;
    }

    public void enrichIndividualObjectWitUserDetails(User user, List<Individual> individualList)
    {
        Individual individual = individualList.get(0);
        individual.setUserId(String.valueOf(user.getId()));
        individual.setUserUuid(user.getUuid());
        if(individual.getUserDetails()==null)
        {
            UserDetails userDetails = UserDetails.builder().userType(UserType.valueOf(user.getType()))
                    .username(user.getUserName()).tenantId(user.getTenantId()).build();
            individual.setUserDetails(userDetails);
        }
    }

    private Function<Individual, UserRequest> toUserRequest() {
        return individual -> IndividualMapper
                .toUserRequest(individual,
                        individualProperties);
    }
}
