package org.egov.project.validator.project;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectRequest;
import org.egov.common.models.project.ProjectType;
import org.egov.common.service.MdmsService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.service.ProjectService;
import org.egov.project.util.BoundaryUtil;
import org.egov.project.util.MDMSUtils;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.project.Constants.INTERNAL_SERVER_ERROR;
import static org.egov.project.Constants.MDMS_RESPONSE;
import static org.egov.project.Constants.PROJECT_TYPES;
import static org.egov.project.util.ProjectConstants.MASTER_DEPARTMENT;
import static org.egov.project.util.ProjectConstants.MASTER_NATUREOFWORK;
import static org.egov.project.util.ProjectConstants.MASTER_PROJECTTYPE;
import static org.egov.project.util.ProjectConstants.MASTER_TENANTS;
import static org.egov.project.util.ProjectConstants.MDMS_COMMON_MASTERS_MODULE_NAME;
import static org.egov.project.util.ProjectConstants.MDMS_TENANT_MODULE_NAME;
import static org.egov.project.util.ProjectConstants.MASTER_PROJECT_OBSERVATION_TYPE;
import static org.egov.project.util.ProjectConstants.MASTER_PROJECT_CYCLES;

// keep cached task for some specific time, use those task to verify whether past task is updated with respect to multi round fields else update the cached map from db first and then verify next using that record.

@Component
@Slf4j
public class MultiRoundProjectValidator {

    private final MdmsService mdmsService;

    private final ProjectService projectService;

    private final ProjectConfiguration projectConfiguration;



    public MultiRoundProjectValidator(MdmsService mdmsService, ProjectService projectService, ProjectConfiguration projectConfiguration) {
        this.mdmsService = mdmsService;
        this.projectService = projectService;
        this.projectConfiguration = projectConfiguration;
    }

    public List<ProjectType> getProjectTypes(String tenantId, RequestInfo requestInfo) {
        JsonNode response = fetchMdmsResponse(requestInfo, tenantId, PROJECT_TYPES,
                projectConfiguration.getMdmsModule());
        return convertToProjectTypeList(response);
    }

    private JsonNode fetchMdmsResponse(RequestInfo requestInfo, String tenantId, String name,
                                       String moduleName) {
        MdmsCriteriaReq serviceRegistry = getMdmsRequest(requestInfo, tenantId, name, moduleName);
        try {
            return mdmsService.fetchConfig(serviceRegistry, JsonNode.class).get(MDMS_RESPONSE);
        } catch (Exception e) {
            throw new CustomException(INTERNAL_SERVER_ERROR, "Error while fetching mdms config");
        }
    }

    private List<ProjectType> convertToProjectTypeList(JsonNode jsonNode) {
        JsonNode projectTypesNode = jsonNode.get(projectConfiguration.getMdmsModule()).withArray(PROJECT_TYPES);
        return new ObjectMapper().convertValue(projectTypesNode, new TypeReference<List<ProjectType>>() {
        });
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
        mdmsCriteria.setTenantId(tenantId.split("\\.")[0]);
        mdmsCriteria.setModuleDetails(moduleDetailList);
        MdmsCriteriaReq mdmsCriteriaReq = new MdmsCriteriaReq();
        mdmsCriteriaReq.setMdmsCriteria(mdmsCriteria);
        mdmsCriteriaReq.setRequestInfo(requestInfo);
        return mdmsCriteriaReq;
    }

//    public void validate
}