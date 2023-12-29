package org.egov.transformer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.UserDetailResponse;
import digit.models.coremodels.UserSearchRequest;
import digit.models.coremodels.mdms.*;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import static org.egov.transformer.Constants.PROJECT_STAFF_ROLES;
@Slf4j
@Service
public class UserService {

    private final ServiceRequestClient restRepo;

    private final String host;

    private final String searchUrl;
    private final MdmsService mdmsService;
    private final String moduleName;
    @Autowired
    public UserService(ServiceRequestClient restRepo,
                       @Value("${egov.user.host}") String host,
                       @Value("${egov.search.user.url}") String searchUrl, MdmsService mdmsService, @Value("${project.staff.role.mdms.module}") String moduleName) {

        this.restRepo = restRepo;
        this.host = host;
        this.searchUrl = searchUrl;
        this.mdmsService = mdmsService;
        this.moduleName = moduleName;
    }

    public List<User> getUsers(String tenantId, String userId){
        UserSearchRequest searchRequest = new UserSearchRequest();
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();
        List<String> Ids = new ArrayList<>();
        Ids.add(userId);
        searchRequest.setRequestInfo(requestInfo);
        searchRequest.setTenantId(tenantId);
        searchRequest.setUuid(Ids);
        try {
            UserDetailResponse response = restRepo.fetchResult(
                    new StringBuilder(host + searchUrl),
                    searchRequest,
                    UserDetailResponse.class
            );
            return response.getUser();
        } catch (Exception e) {
            log.error("Exception while searching users : {}", ExceptionUtils.getStackTrace(e));
        }
        return new ArrayList<>();
    }

    public HashMap<String, Integer> getProjectStaffRoles(String tenantId){
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();
        MdmsCriteriaReq mdmsCriteriaReq = getMdmsRequest(requestInfo, tenantId, PROJECT_STAFF_ROLES, moduleName);
        JSONArray projectStaffRoles = new JSONArray();
        try {
            MdmsResponse mdmsResponse = mdmsService.fetchConfig(mdmsCriteriaReq, MdmsResponse.class);
            projectStaffRoles = mdmsResponse.getMdmsRes().get(moduleName).get(PROJECT_STAFF_ROLES);
        } catch (Exception e) {
            log.error("Exception while fetching mdms roles: {}", ExceptionUtils.getStackTrace(e));
        }

        HashMap<String,Integer> projectStaffRolesMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        projectStaffRoles.forEach(role -> {
            LinkedHashMap<String,Object> map = objectMapper.convertValue(role, new TypeReference<LinkedHashMap>() {});
            projectStaffRolesMap.put((String) map.get("code"), (Integer) map.get("rank"));
        });
        return projectStaffRolesMap;
    }

    public String getStaffRole(String tenantId, List<User> users){

        List<String> userRoles = new ArrayList<>();
        if(users != null && users.size() > 0){
            users.get(0).getRoles().forEach(role -> userRoles.add(role.getCode()));
        }

        HashMap<String, Integer> projectStaffRolesMap = getProjectStaffRoles(tenantId);
        String roleByRank = null;
        int minValue = Integer.MAX_VALUE;

        if(userRoles.size() > 0){
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

    public String getUserName (List<User> users, String userId){
        if (users!= null && users.size()>0){
            return users.get(0).getUserName();
        }
        return userId;
    }
    private MdmsCriteriaReq getMdmsRequest(RequestInfo requestInfo, String tenantId, String masterName,
                                           String moduleName) {
        MasterDetail masterDetail = new MasterDetail();
        masterDetail.setName(masterName);
        List<MasterDetail> masterDetailList = new ArrayList<>();
        masterDetailList.add(masterDetail);
        ModuleDetail moduleDetail = new ModuleDetail();
        moduleDetail.setMasterDetails(masterDetailList);
        moduleDetail.setModuleName(moduleName);
        List<ModuleDetail> moduleDetailList = new ArrayList<>();
        moduleDetailList.add(moduleDetail);
        MdmsCriteria mdmsCriteria = new MdmsCriteria();
        mdmsCriteria.setTenantId(tenantId);
        mdmsCriteria.setModuleDetails(moduleDetailList);
        MdmsCriteriaReq mdmsCriteriaReq = new MdmsCriteriaReq();
        mdmsCriteriaReq.setMdmsCriteria(mdmsCriteria);
        mdmsCriteriaReq.setRequestInfo(requestInfo);
        return mdmsCriteriaReq;
    }
}