package org.egov.project.validator.project;

import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectRequest;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.util.BoundaryUtil;
import org.egov.project.util.MDMSUtils;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.project.util.ProjectConstants.MASTER_DEPARTMENT;
import static org.egov.project.util.ProjectConstants.MASTER_NATUREOFWORK;
import static org.egov.project.util.ProjectConstants.MASTER_PROJECTTYPE;
import static org.egov.project.util.ProjectConstants.MASTER_TENANTS;
import static org.egov.project.util.ProjectConstants.MDMS_COMMON_MASTERS_MODULE_NAME;
import static org.egov.project.util.ProjectConstants.MDMS_TENANT_MODULE_NAME;
import static org.egov.project.util.ProjectConstants.MASTER_PROJECT_OBSERVATION_TYPE;
import static org.egov.project.util.ProjectConstants.MASTER_PROJECT_CYCLES;


@Component
@Slf4j
public class MultiRoundProjectValidator extends ProjectValidator {

//    @Autowired
//    MDMSUtils mdmsUtils;
//
//    @Autowired
//    BoundaryUtil boundaryUtil;
//
//    @Autowired
//    ProjectConfiguration config;

    @Override
    public void validateCreateProjectRequest(ProjectRequest request) {
        Map<String, String> errorMap = new HashMap<>();
        RequestInfo requestInfo = request.getRequestInfo();

        //Verify if RequestInfo and UserInfo is present
        validateRequestInfo(requestInfo);
        //Verify if project request and mandatory fields are present
        validateProjectRequest(request.getProjects());
        //Verify if project request have multiple tenant Ids
        validateMultipleTenantIds(request);

        String tenantId = request.getProjects().get(0).getTenantId();
        //Verify MDMS Data
        validateRequestMDMSData(request, tenantId, errorMap);

        //Get boundaries in list from all Projects in request body for validation
        Map<String, List<String>> boundariesForValidation = getBoundaryForValidation(request.getProjects());
        validateBoundary(boundariesForValidation, tenantId, requestInfo, errorMap);
        log.info("Boundaries in request validated with Location Service");

        // Verify provided documentIds are valid.
        validateDocumentIds(request);

        if (!errorMap.isEmpty())
            throw new CustomException(errorMap);
    }

    @Override
    public void validateSearchProjectRequest(ProjectRequest project, Integer limit, Integer offset, String tenantId, Long createdFrom, Long createdTo) {
        Map<String, String> errorMap = new HashMap<>();
        RequestInfo requestInfo = project.getRequestInfo();

        //Verify if RequestInfo and UserInfo is present
        validateRequestInfo(requestInfo);
        //Verify if search project request parameters are valid
        validateSearchProjectRequestParams(limit, offset, tenantId, createdFrom, createdTo);
        //Verify if search project request is valid
        validateSearchProjectRequest(project.getProjects(), tenantId, createdFrom);
        //Verify if project request have multiple tenant Ids
        validateMultipleTenantIds(project);
        //Verify MDMS Data
        validateRequestMDMSData(project, tenantId, errorMap);

        if (!errorMap.isEmpty())
            throw new CustomException(errorMap);
    }

    @Override
    public void validateUpdateProjectRequest(ProjectRequest request) {
        Map<String, String> errorMap = new HashMap<>();
        RequestInfo requestInfo = request.getRequestInfo();

        //Verify if RequestInfo and UserInfo is present
        validateRequestInfo(requestInfo);
        //Verify Project request and if mandatory fields are present
        validateProjectRequest(request.getProjects());
        //Verify if project request have multiple tenant Ids
        validateMultipleTenantIds(request);

        //Verify if Project id is present
        for (Project project: request.getProjects()) {
            if (StringUtils.isBlank(project.getId())) {
                log.error("Project Id is mandatory");
                throw new CustomException("UPDATE_PROJECT", "Project Id is mandatory");
            }
        }

        String tenantId = request.getProjects().get(0).getTenantId();
        //Verify MDMS Data
         validateRequestMDMSData(request, tenantId, errorMap);

        //Get boundaries in list from all Projects in request body for validation
        Map<String, List<String>> boundariesForValidation = getBoundaryForValidation(request.getProjects());
        validateBoundary(boundariesForValidation, tenantId, requestInfo, errorMap);
        log.info("Boundaries in request validated with Location Service");

        // Verify provided documentIds are valid.
        validateDocumentIds(request);


        if (!errorMap.isEmpty())
            throw new CustomException(errorMap);
    }

    @Override
    public void validateUpdateAgainstDB(List<Project> projectsFromRequest, List<Project> projectsFromDB) {
        super.validateUpdateAgainstDB(projectsFromRequest, projectsFromDB);
    }

    @Override
    public void validateParentAgainstDB(List<Project> projects, List<Project> parentProjects) {
        super.validateParentAgainstDB(projects, parentProjects);
    }

    /* Validate Project Request MDMS data */
    @Override
    public void validateRequestMDMSData(ProjectRequest request, String tenantId, Map<String, String> errorMap) {
        String rootTenantId = tenantId.split("\\.")[0];

        //Get MDMS data using create project request and tenantId
        Object mdmsData = mdmsUtils.mDMSCall(request, rootTenantId);

        validateMDMSData(request.getProjects(), mdmsData,  errorMap);
        log.info("Request data validated with MDMS");
    }

    /* Validates the request data against MDMS data */
    @Override
    public void  validateMDMSData(List<Project> projects, Object mdmsData, Map<String, String> errorMap) {
        final String jsonPathForMDMSTypeOfProjectList = "$.MdmsRes." + config.getMdmsModule() + "." + MASTER_PROJECTTYPE + ".[?(@.active==true)].code";
        final String jsonPathForMDMSNatureOfWorkList = "$.MdmsRes." + config.getMdmsModule() + "." + MASTER_NATUREOFWORK + ".[?(@.active==true)].code";
        final String jsonPathForDepartment = "$.MdmsRes." + MDMS_COMMON_MASTERS_MODULE_NAME + "." + MASTER_DEPARTMENT + ".*.code";
        final String jsonPathForTenants = "$.MdmsRes." + MDMS_TENANT_MODULE_NAME + "." + MASTER_TENANTS + ".*";
        final String jsonPathForObeservationTypes = "$.MdmsRes." + config.getMdmsModule() + "." + MASTER_PROJECT_OBSERVATION_TYPE + ".*";
        final String jsonPathForCycles = "$.MdmsRes." + config.getMdmsModule() + "." + MASTER_PROJECT_CYCLES + ".*";

        List<Object> deptRes = null;
        List<Object> typeOfProjectRes = null;
        List<Object> tenantRes = null;
        List<Object> natureOfWorkRes = null;
        List<Object> subTypeOfProjectRes = null;
        List<Object> cyclesForMultiRound = null;
        try {
            deptRes = JsonPath.read(mdmsData, jsonPathForDepartment);
            typeOfProjectRes = JsonPath.read(mdmsData, jsonPathForMDMSTypeOfProjectList);
            subTypeOfProjectRes = JsonPath.read(mdmsData, jsonPathForObeservationTypes);
            tenantRes = JsonPath.read(mdmsData, jsonPathForTenants);
            if (projects.stream().anyMatch(p -> StringUtils.isNotBlank(p.getNatureOfWork()))) {
                natureOfWorkRes = JsonPath.read(mdmsData, jsonPathForMDMSNatureOfWorkList);
            }
            cyclesForMultiRound = JsonPath.read(mdmsData, jsonPathForCycles);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException("JSONPATH_ERROR", "Failed to parse mdms response");
        }

        for (Project project: projects) {
            log.info("Validate Project type with MDMS");
            if (!StringUtils.isBlank(project.getProjectType()) && !typeOfProjectRes.contains(project.getProjectType())) {
                log.error("The project type: " + project.getProjectType() + " is not present in MDMS");
                errorMap.put("INVALID_PROJECT_TYPE", "The project type: " + project.getProjectType() + " is not present in MDMS");
            }
            log.info("Validate Tenant Id with MDMS");
            if (!StringUtils.isBlank(project.getTenantId()) && !tenantRes.contains(project.getTenantId())) {
                log.error("The tenant: " + project.getTenantId() + " is not present in MDMS");
                errorMap.put("INVALID_TENANT", "The tenant: " + project.getTenantId() + " is not present in MDMS");
            }
            log.info("Validate Department with MDMS");
            if (!StringUtils.isBlank(project.getDepartment()) && !deptRes.contains(project.getDepartment())) {
                log.error("The department code: " + project.getDepartment() + " is not present in MDMS");
                errorMap.put("INVALID_DEPARTMENT_CODE", "The department code: " + project.getDepartment() + " is not present in MDMS");
            }

            //Verify if project nature of work is present for project type
            log.info("Validate Nature of Work with MDMS");
            if (!StringUtils.isBlank(project.getNatureOfWork()) && natureOfWorkRes != null && !natureOfWorkRes.contains(project.getNatureOfWork())) {
                log.error("The nature of work: " + project.getNatureOfWork() + " is not present in MDMS");
                errorMap.put("INVALID_NATURE_OF_WORK", "The nature of work: " + project.getNatureOfWork() + " is not present in MDMS");
            }

            //Verify if project subtype/observation type is present for project type
            log.info("Validate Project observation type with MDMS");
            if (!StringUtils.isBlank(project.getProjectSubType()) && !subTypeOfProjectRes.contains(project.getProjectSubType())) {
                log.error("The project observation type: " + project.getProjectSubType() + " is not present in MDMS");
                errorMap.put("INVALID_PROJECT_SUB_TYPE", "The project observation type: " + project.getProjectSubType() + " is not present in MDMS");
            } else {

            }

        }
    }

}
