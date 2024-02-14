package org.egov.transformer.service;

import org.egov.transformer.models.user.UserSearchRequest;
import org.egov.transformer.models.user.UserSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
//import org.egov.transformer.models.user.RequestInfo;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.models.user.UserSearchResponseContent;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.egov.transformer.Constants.*;

@Slf4j
@Service
public class UserService {

    private final ServiceRequestClient restRepo;

    private final String host;

    private final String searchUrl;

    private final CommonUtils commonUtils;
    private static Map<String, Map<String, String>> userIdVsUserInfoCache = new ConcurrentHashMap<>();
    private static final int MAX_RETRY_COUNT = 10;
    private static final int RETRY_DELAY_MS = 5000;

    @Autowired
    public UserService(ServiceRequestClient restRepo,
                       @Value("${egov.user.host}") String host,
                       @Value("${egov.search.user.url}") String searchUrl, CommonUtils commonUtils) {

        this.restRepo = restRepo;
        this.host = host;
        this.searchUrl = searchUrl;
        this.commonUtils = commonUtils;
    }


    public Map<String, String> getUserInfo(String tenantId, String userId) {
        List<UserSearchResponseContent> users;
        Map<String, String> userMap = new HashMap<>();
        Map<String, String> userDetailsMap = new HashMap<>();
        String userName = null;
        String role;

        if (userIdVsUserInfoCache.containsKey(userId)) {
            log.info("fetching from userIdVsUserInfoCache for userId: " + userId);
            userDetailsMap = userIdVsUserInfoCache.get(userId);
            return userDetailsMap;
        } else {
            users = getUsers(tenantId, userId);
            if (users.isEmpty()) {
                log.info("unable to fetch users for userId: " + userId);
                userMap.put(USERNAME, userId);
                userMap.put(NAME, null);
                userMap.put(ROLE, null);
                userMap.put(ID, null);
                userMap.put("city", null);
                return userMap;
            }
            userName = users.get(0).getUserName();
            role = getStaffRole(tenantId, users);
            userMap.put(USERNAME, userName);
            userMap.put(NAME, users.get(0).getName());
            userMap.put(ROLE, role);
            userMap.put(ID, String.valueOf(users.get(0).getId()));
            userMap.put("city", users.get(0).getCorrespondenceAddress());
            userIdVsUserInfoCache.put(userId, userMap);
            return userMap;
        }
    }

    public List<UserSearchResponseContent> getUsers(String tenantId, String userId) {
        UserSearchRequest searchRequest = new UserSearchRequest();
        List<String> Ids = new ArrayList<>();
        Ids.add(userId);
//        searchRequest.setRequestInfo(requestInfo);
        searchRequest.setTenantId(tenantId);
        searchRequest.setUuid(Ids);
        try {
            UserSearchResponse response = restRepo.fetchResult(
                    new StringBuilder(host + searchUrl),
                    searchRequest,
                    UserSearchResponse.class
            );
            return Collections.singletonList(response.getUserSearchResponseContent().get(0));
//            return response.getUserSearchResponseContent().get(0);
        } catch (Exception e) {
            log.error("Exception while searching users : {}", ExceptionUtils.getStackTrace(e));
        }
        return new ArrayList<>();
    }



    public String getStaffRole(String tenantId, List<UserSearchResponseContent> users) {

        List<String> userRoles = new ArrayList<>();
        if (users != null && users.size() > 0) {
            users.get(0).getRoles().forEach(role -> userRoles.add(role.getCode()));
        }

        HashMap<String, Integer> projectStaffRolesMap = commonUtils.getProjectStaffRoles(tenantId);
        String roleByRank = null;
        int minValue = Integer.MAX_VALUE;

        if (userRoles.size() > 0) {
            for (String element : userRoles) {
                if (projectStaffRolesMap.containsKey(element)) {
                    int value = projectStaffRolesMap.get(element);
                    if (value < minValue) {
                        minValue = value;
                        roleByRank = element;
                    }
                }
            }
        }
        return roleByRank;
    }

    public Long getUserServiceId(String tenantId, String userId) {
        Map<String, String> userMap = getUserInfo(tenantId, userId);
        if (userMap.containsKey(ID) && userMap.get(ID) != null) {
            return Long.valueOf(userMap.get(ID));
        }
        List<UserSearchResponseContent> users = retryGetUsers(tenantId, userId);

        if (!users.isEmpty()) {
            return users.get(0).getId();
        }

        return null;
    }



    private List<UserSearchResponseContent> retryGetUsers(String tenantId, String userId) {
        int retryCount = 0;
        List<UserSearchResponseContent> users = getUsers(tenantId, userId);

        while (users.isEmpty() && retryCount < MAX_RETRY_COUNT) {
            log.info("Retrying fetching user for userid: {}, RETRY_COUNT: {} - MAX_RETRY_COUNT: {}, DELAY: {}", userId, retryCount, MAX_RETRY_COUNT, RETRY_DELAY_MS);
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            users = getUsers(tenantId, userId);
            retryCount++;
        }
        Map<String, String> userMap = new HashMap<>();
        userMap.put(ID, userId);
        userMap.put(ROLE, getStaffRole(tenantId, users));
        userMap.put(USERNAME, users.get(0).getUserName());
        userIdVsUserInfoCache.put(userId, userMap);

        return users;
    }
}