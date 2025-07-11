package org.egov.pgr.service;


import org.egov.common.contract.request.RequestInfo;
import org.egov.pgr.config.PGRConfiguration;
import org.egov.pgr.util.UserUtils;
import org.egov.pgr.web.models.RequestSearchCriteria;
import org.egov.pgr.web.models.ServiceRequest;
import org.egov.pgr.web.models.ServiceWrapper;
import org.egov.pgr.web.models.User;
import org.egov.pgr.web.models.user.CreateUserRequest;
import org.egov.pgr.web.models.user.UserDetailResponse;
import org.egov.pgr.web.models.user.UserSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
public class UserService {


    private UserUtils userUtils;

    private PGRConfiguration config;

    @Autowired
    public UserService(UserUtils userUtils, PGRConfiguration config) {
        this.userUtils = userUtils;
        this.config = config;
    }

    /**
     * Calls user service to enrich user from search or upsert user
     * @param request
     */
    public void callUserService(ServiceRequest request){

        if(!StringUtils.isEmpty(request.getService().getAccountId())) {
            enrichUser(request);
        }
        else if(request.getService().getUser()!=null) {
            upsertUser(request);
        }

    }

    /**
     * Calls user search to fetch the list of user and enriches it in serviceWrappers
     * @param serviceWrappers
     */
    public void enrichUsers(List<ServiceWrapper> serviceWrappers){

        Set<String> uuids = new HashSet<>();
        if (CollectionUtils.isEmpty(serviceWrappers)) return;
        String tenantId = serviceWrappers.get(0).getService().getTenantId();
        serviceWrappers.forEach(serviceWrapper -> {
            uuids.add(serviceWrapper.getService().getAccountId());
        });

        Map<String, User> idToUserMap = searchBulkUser(tenantId, new LinkedList<>(uuids));

        serviceWrappers.forEach(serviceWrapper -> {
            serviceWrapper.getService().setUser(idToUserMap.get(serviceWrapper.getService().getAccountId()));
        });

    }


    /**
     * Creates or updates the user based on if the user exists. The user existance is searched based on userName = mobileNumber
     * If the there is already a user with that mobileNumber, the existing user is updated
     * @param request
     */
    private void upsertUser(ServiceRequest request){

        User user = request.getService().getUser();
        String tenantId = request.getService().getTenantId();
        User userServiceResponse = null;

        // Search on mobile number as user name
        UserDetailResponse userDetailResponse = searchUser(userUtils.getStateLevelTenant(tenantId),null,
                user.getMobileNumber(), request.getService().getUser().getType());
        if (!userDetailResponse.getUser().isEmpty()) {
            User userFromSearch = userDetailResponse.getUser().get(0);
            if(!user.getName().equalsIgnoreCase(userFromSearch.getName())){
                userServiceResponse = updateUser(request.getRequestInfo(),user,userFromSearch);
            }
            else userServiceResponse = userDetailResponse.getUser().get(0);
        }
        else {
            userServiceResponse = createUser(request.getRequestInfo(),tenantId, user, request.getService().getUser().getType());
        }

        // Enrich the accountId
        request.getService().setAccountId(userServiceResponse.getUuid());
    }

    /**
     * Calls user search to fetch a user and enriches it in request
     * @param request
     */
    private void enrichUser(ServiceRequest request){

        RequestInfo requestInfo = request.getRequestInfo();
        String accountId = request.getService().getAccountId();
        String tenantId = request.getService().getTenantId();

        UserDetailResponse userDetailResponse = searchUser(userUtils.getStateLevelTenant(tenantId),accountId,
                null, request.getService().getUser().getType());

        if(userDetailResponse.getUser().isEmpty())
            throw new CustomException("INVALID_ACCOUNTID","No user exist for the given accountId");

        else request.getService().setUser(userDetailResponse.getUser().get(0));

    }

    /**
     * Creates the user from the given userInfo by calling user service
     * @param requestInfo
     * @param tenantId
     * @param userInfo
     * @param userType
     * @return
     */
    private User createUser(RequestInfo requestInfo,String tenantId, User userInfo, String userType) {

        userUtils.addUserDefaultFields(userInfo.getMobileNumber(),tenantId, userInfo, userType);
        StringBuilder uri = new StringBuilder(config.getUserHost())
                .append(config.getUserContextPath())
                .append(config.getUserCreateEndpoint());


        UserDetailResponse userDetailResponse = userUtils.userCall(new CreateUserRequest(requestInfo, userInfo), uri);

        return userDetailResponse.getUser().get(0);
    }

    /**
     * Updates the given user by calling user service
     * @param requestInfo
     * @param user
     * @param userFromSearch
     * @return
     */
    private User updateUser(RequestInfo requestInfo,User user,User userFromSearch) {

        userFromSearch.setName(user.getName());
        userFromSearch.setActive(true);

        StringBuilder uri = new StringBuilder(config.getUserHost())
                .append(config.getUserContextPath())
                .append(config.getUserUpdateEndpoint());


        UserDetailResponse userDetailResponse = userUtils.userCall(new CreateUserRequest(requestInfo, userFromSearch), uri);

        return userDetailResponse.getUser().get(0);

    }

    /**
     * calls the user search API based on the given accountId and userName
     * @param stateLevelTenant
     * @param accountId
     * @param userName
     * @param userType
     * @return
     */
    private UserDetailResponse searchUser(String stateLevelTenant, String accountId, String userName, String userType){

        UserSearchRequest userSearchRequest =new UserSearchRequest();
        userSearchRequest.setActive(true);
        userSearchRequest.setUserType(userType);
        userSearchRequest.setTenantId(stateLevelTenant);

        if(StringUtils.isEmpty(accountId) && StringUtils.isEmpty(userName))
            return null;

        if(!StringUtils.isEmpty(accountId))
            userSearchRequest.setUuid(Collections.singletonList(accountId));

        if(!StringUtils.isEmpty(userName))
            userSearchRequest.setUserName(userName);

        StringBuilder uri = new StringBuilder(config.getUserHost()).append(config.getUserSearchEndpoint());
        return userUtils.userCall(userSearchRequest,uri);
    }

    /**
     * Searches for users based on their unique identifiers (UUIDs) within a specific tenant
     * and returns a mapping of UUIDs to their corresponding user details.
     *
     * @param tenantId the ID of the tenant to which the users belong
     * @param uuids a list of unique user identifiers to be searched
     * @return a map where the keys are user UUIDs and the values are the corresponding User objects
     * @throws CustomException if no users are found for the provided UUIDs
     */
    private Map<String,User> searchBulkUser(String tenantId, List<String> uuids){

        UserSearchRequest userSearchRequest =new UserSearchRequest();
        userSearchRequest.setActive(true);
        userSearchRequest.setTenantId(tenantId);


        if(!CollectionUtils.isEmpty(uuids))
            userSearchRequest.setUuid(uuids);


        StringBuilder uri = new StringBuilder(config.getUserHost()).append(config.getUserSearchEndpoint());
        UserDetailResponse userDetailResponse = userUtils.userCall(userSearchRequest,uri);
        List<User> users = userDetailResponse.getUser();

        if(CollectionUtils.isEmpty(users))
            throw new CustomException("USER_NOT_FOUND","No user found for the uuids");

        Map<String,User> idToUserMap = users.stream().collect(Collectors.toMap(User::getUuid, Function.identity()));

        return idToUserMap;
    }

    /**
     * Enriches the list of userUuids associated with the mobileNumber in the search criteria
     * @param tenantId
     * @param criteria
     */
    public void enrichUserIds(String tenantId, RequestSearchCriteria criteria){

        String mobileNumber = criteria.getMobileNumber();

        UserSearchRequest userSearchRequest =new UserSearchRequest();
        userSearchRequest.setActive(true);
        userSearchRequest.setTenantId(tenantId);
        userSearchRequest.setMobileNumber(mobileNumber);

        StringBuilder uri = new StringBuilder(config.getUserHost()).append(config.getUserSearchEndpoint());
        UserDetailResponse userDetailResponse = userUtils.userCall(userSearchRequest,uri);
        List<User> users = userDetailResponse.getUser();

        Set<String> userIds = users.stream().map(User::getUuid).collect(Collectors.toSet());
        criteria.setUserIds(userIds);
    }

}
