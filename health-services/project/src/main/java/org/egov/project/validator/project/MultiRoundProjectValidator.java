package org.egov.project.validator.project;

import com.fasterxml.jackson.databind.JsonNode;
import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.service.MdmsService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.egov.project.Constants.INTERNAL_SERVER_ERROR;
import static org.egov.project.Constants.MDMS_RESPONSE;
import static org.egov.project.Constants.PROJECT_TYPES;

@Component
@Slf4j
public class MultiRoundProjectValidator {

    private final MdmsService mdmsService;
        private final ProjectConfiguration projectConfiguration;



    public MultiRoundProjectValidator(MdmsService mdmsService, ProjectConfiguration projectConfiguration) {
        this.mdmsService = mdmsService;
        this.projectConfiguration = projectConfiguration;
    }

    public Map<String, JsonNode> getProjectTypes(String tenantId, RequestInfo requestInfo) {
        JsonNode response = fetchMdmsResponse(requestInfo, tenantId, PROJECT_TYPES,
                projectConfiguration.getMdmsModule());
        return convertToProjectTypeMap(response);
    }

    private JsonNode fetchMdmsResponse(RequestInfo requestInfo, String tenantId, String name,
                                       String moduleName) {
        MdmsCriteriaReq serviceRegistry = getMdmsRequest(requestInfo, tenantId, name, moduleName);
        try {
            return mdmsService.fetchConfig(serviceRegistry, JsonNode.class).get(MDMS_RESPONSE);
        } catch (Exception e) {
            throw new CustomException(INTERNAL_SERVER_ERROR, "Error while fetching MDMS config");
        }
    }

    private Map<String, JsonNode> convertToProjectTypeMap(JsonNode jsonNode) {
        JsonNode projectTypesNode = jsonNode.get(projectConfiguration.getMdmsModule()).withArray(PROJECT_TYPES);
        Map<String, JsonNode> map = new HashMap<>();
        projectTypesNode.forEach(json -> map.put(json.get("id").textValue(), json));
        return map;
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

    public Map<String, JsonNode> populateProjectTypeMap(Set<String> tenantIdSet, RequestInfo requestInfo) {
        Map<String, JsonNode> projectTypeMap = new HashMap<>();
        for(String tenant : tenantIdSet) {
            projectTypeMap.putAll(this.getProjectTypes(tenant, requestInfo));
        }
        return projectTypeMap;
    }
}