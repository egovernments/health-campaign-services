package org.egov.transformer.service;

import org.egov.transformer.models.user.UserSearchRequest;
import org.egov.transformer.models.user.UserSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.models.user.UserSearchResponseContent;
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
    private final MdmsService mdmsService;
    private static Map<String, Map<String, String>> userIdVsUserInfoCache = new ConcurrentHashMap<>();
    private static final int MAX_RETRY_COUNT = 10;
    private static final int RETRY_DELAY_MS = 5000;

    @Autowired
    public UserService(ServiceRequestClient restRepo,
                       @Value("${egov.user.host}") String host,
                       @Value("${egov.search.user.url}") String searchUrl, MdmsService mdmsService) {

        this.restRepo = restRepo;
        this.host = host;
        this.searchUrl = searchUrl;
        this.mdmsService = mdmsService;
    }


    public Map<String, String> getUserInfo(String tenantId, String userId) {
        List<UserSearchResponseContent> users;
        Map<String, String> userMap = new HashMap<>();
        Map<String, String> userDetailsMap;
        String userName;
        StringBuilder role = new StringBuilder();

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
                userMap.put(CITY, null);
                return userMap;
            }
            userName = users.get(0).getUserName();
            users.get(0).getRoles().forEach(r -> {
                role.append(r.getCode()).append("_");
            });

            userMap.put(USERNAME, userName);
            userMap.put(NAME, users.get(0).getName());
            userMap.put(ROLE, role.toString());
            userMap.put(ID, String.valueOf(users.get(0).getId()));
            userMap.put(CITY, users.get(0).getCorrespondenceAddress());
            userIdVsUserInfoCache.put(userId, userMap);
            return userMap;
        }
    }

    public List<UserSearchResponseContent> getUsers(String tenantId, String userId) {
        UserSearchRequest searchRequest = new UserSearchRequest();
        List<String> Ids = new ArrayList<>();
        Ids.add(userId);
        searchRequest.setTenantId(tenantId);
        searchRequest.setUuid(Ids);
        try {
            UserSearchResponse response = restRepo.fetchResult(
                    new StringBuilder(host + searchUrl),
                    searchRequest,
                    UserSearchResponse.class
            );
            List<UserSearchResponseContent> responseContent = response.getUserSearchResponseContent();
            if (responseContent != null && !responseContent.isEmpty()) {
                return Collections.singletonList(responseContent.get(0));
            } else {
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("Exception while searching users : {}", ExceptionUtils.getStackTrace(e));
        }
        return new ArrayList<>();
    }



    public String getStaffRole(String tenantId, List<UserSearchResponseContent> users) {

        List<String> userRoles = new ArrayList<>();
        if (users != null && !users.isEmpty()) {
            users.get(0).getRoles().forEach(role -> userRoles.add(role.getCode()));
        }
        // No need to fetch roles ranking for single role user
        if (userRoles.size() == 1) {
            return userRoles.get(0);
        }

        HashMap<String, Integer> projectStaffRolesMap = mdmsService.getProjectStaffRoles(tenantId);
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