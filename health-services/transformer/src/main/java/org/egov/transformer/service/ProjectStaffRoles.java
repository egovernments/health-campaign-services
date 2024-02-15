package org.egov.transformer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.mdms.*;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import static org.egov.transformer.Constants.PROJECT_STAFF_ROLES;
@Slf4j
@Service
public class ProjectStaffRoles {
    private final MdmsService mdmsService;
    private final String moduleName;

    public ProjectStaffRoles(MdmsService mdmsService, @Value("${project.staff.role.mdms.module}") String moduleName) {
        this.mdmsService = mdmsService;
        this.moduleName = moduleName;
    }

    public HashMap<String, Integer> getProjectStaffRoles(String tenantId) {
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

        HashMap<String, Integer> projectStaffRolesMap = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        projectStaffRoles.forEach(role -> {
            LinkedHashMap<String, Object> map = objectMapper.convertValue(role, new TypeReference<LinkedHashMap>() {
            });
            projectStaffRolesMap.put((String) map.get("code"), (Integer) map.get("rank"));
        });
        return projectStaffRolesMap;
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
