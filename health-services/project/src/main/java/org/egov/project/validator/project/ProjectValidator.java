package org.egov.project.validator.project;

import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.util.BoundaryUtil;
import org.egov.project.util.MDMSUtils;
import org.egov.project.web.models.Document;
import org.egov.project.web.models.Project;
import org.egov.project.web.models.ProjectRequest;
import org.egov.project.web.models.Target;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.egov.project.util.ProjectConstants.BOUNDARY_ADMIN_HIERARCHY_CODE;
import static org.egov.project.util.ProjectConstants.MASTER_DEPARTMENT;
import static org.egov.project.util.ProjectConstants.MASTER_PROJECTTYPE;
import static org.egov.project.util.ProjectConstants.MASTER_TENANTS;
import static org.egov.project.util.ProjectConstants.MDMS_COMMON_MASTERS_MODULE_NAME;
import static org.egov.project.util.ProjectConstants.MDMS_TENANT_MODULE_NAME;
import static org.egov.project.util.ProjectConstants.MDMS_WORKS_MODULE_NAME;

@Component
@Slf4j
public class ProjectValidator {

    @Autowired
    MDMSUtils mdmsUtils;

    @Autowired
    BoundaryUtil boundaryUtil;

    @Autowired
    ProjectConfiguration config;

    /* Validates create Project request body */
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

        //Get localities in list from all Projects in request body for validation
        List<String> locationsForValidation = getLocationForValidation(request.getProjects());
        validateLocation(locationsForValidation, tenantId, requestInfo, errorMap);
        log.info("Localities in request validated with Location Service");

        // Verify provided documentIds are valid.
        validateDocumentIds(request);

        if (!errorMap.isEmpty())
            throw new CustomException(errorMap);
    }

    /* Validates search Project request body and parameters*/
    public void validateSearchProjectRequest(ProjectRequest project, Integer limit, Integer offset, String tenantId) {
        Map<String, String> errorMap = new HashMap<>();
        RequestInfo requestInfo = project.getRequestInfo();

        //Verify if RequestInfo and UserInfo is present
        validateRequestInfo(requestInfo);
        //Verify if search project request parameters are valid
        validateSearchProjectRequestParams(limit, offset, tenantId);
        //Verify if search project request is valid
        validateSearchProjectRequest(project.getProjects(), tenantId);
        //Verify if project request have multiple tenant Ids
        validateMultipleTenantIds(project);
        //Verify MDMS Data
        validateRequestMDMSData(project, tenantId, errorMap);

        if (!errorMap.isEmpty())
            throw new CustomException(errorMap);
    }

    /* Validates Update Project request body */
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

        //Get localities in list from all Projects in request body for validation
        List<String> locationsForValidation = getLocationForValidation(request.getProjects());
        validateLocation(locationsForValidation, tenantId, requestInfo, errorMap);
        log.info("Localities in request validated with Location Service");

        // Verify provided documentIds are valid.
        validateDocumentIds(request);


        if (!errorMap.isEmpty())
            throw new CustomException(errorMap);
    }

    /* Validates if search Project request parameters are valid */
    private void validateSearchProjectRequestParams(Integer limit, Integer offset, String tenantId) {
        if (limit == null) {
            log.error("limit is mandatory parameter in Project search");
            throw new CustomException("SEARCH_PROJECT.LIMIT", "limit is mandatory for Project Search");
        }

        if (offset == null) {
            log.error("offset is mandatory parameter in Project search");
            throw new CustomException("SEARCH_PROJECT.OFFSET", "offset is mandatory for Project Search");
        }

        if (StringUtils.isBlank(tenantId)) {
            log.error("tenantId is mandatory parameter in Project search");
            throw new CustomException("SEARCH_PROJECT.TENANT_ID", "tenantId is mandatory for Project Search");
        }
    }

    /* Validates Project request body for create and update apis */
    private void validateProjectRequest(List<Project> projects) {
        Map<String, String> errorMap = new HashMap<>();

        if (projects == null || projects.size() == 0) {
            log.error("Project list is empty. Projects is mandatory");
            throw new CustomException("PROJECT", "Projects are mandatory");
        }

        for (Project project: projects) {
            if (project == null) {
                log.error("Project is mandatory in Projects");
                throw new CustomException("PROJECT", "Project is mandatory");
            }
            if (StringUtils.isBlank(project.getTenantId())) {
                log.error("Tenant ID is mandatory in Project request body");
                errorMap.put("TENANT_ID", "Tenant ID is mandatory");
            }
            if ((project.getStartDate() != null && project.getEndDate() != null  && project.getEndDate() != 0) && (project.getStartDate().compareTo(project.getEndDate()) > 0)) {
                log.error("Start date should be less than end date");
                errorMap.put("INVALID_DATE", "Start date should be less than end date");
            }
        }

        if (!errorMap.isEmpty())
            throw new CustomException(errorMap);
    }

    /* Validates Search Project Request body */
    private void validateSearchProjectRequest(List<Project> projects, String tenantId) {
        if (projects == null || projects.size() == 0) {
            log.error("Project list is empty. Projects is mandatory");
            throw new CustomException("PROJECT", "Projects are mandatory");
        }

        for (Project project: projects) {
            if (project == null) {
                log.error("Project is mandatory in Projects");
                throw new CustomException("PROJECT", "Project is mandatory");
            }
            if (StringUtils.isBlank(project.getTenantId())) {
                log.error("Tenant ID is mandatory in Project request body");
                throw new CustomException("TENANT_ID", "Tenant ID is mandatory");
            }
            if (StringUtils.isBlank(project.getId()) && StringUtils.isBlank(project.getProjectType())
                    && StringUtils.isBlank(project.getName()) && StringUtils.isBlank(project.getProjectNumber())
                    && StringUtils.isBlank(project.getProjectSubType())
                    && project.getStartDate() == 0 && project.getEndDate() == 0) {
                log.error("Any one project search field is required for Project Search");
                throw new CustomException("PROJECT_SEARCH_FIELDS", "Any one project search field is required");
            }

            if (!project.getTenantId().equals(tenantId)) {
                log.error("Tenant Id must be same in URL param as well as project request body");
                throw new CustomException("MULTIPLE_TENANTS", "Tenant Id must be same in URL param and project request");
            }

            if ((project.getStartDate() != null && project.getEndDate() != null && project.getEndDate() != 0) && (project.getStartDate().compareTo(project.getEndDate()) > 0)) {
                log.error("Start date should be less than end date");
                throw new CustomException("INVALID_DATE", "Start date should be less than end date");
            }

            if ((project.getStartDate() == null || project.getStartDate() == 0) && (project.getEndDate() != null && project.getEndDate() != 0)) {
                log.error("Start date is required if end date is passed");
                throw new CustomException("INVALID_DATE", "Start date is required if end date is passed");
            }

        }
    }

    /* Validates Request Info and User Info */
    private void validateRequestInfo(RequestInfo requestInfo) {
        if (requestInfo == null) {
            log.error("Request info is mandatory");
            throw new CustomException("REQUEST_INFO", "Request info is mandatory");
        }
        if (requestInfo.getUserInfo() == null) {
            log.error("UserInfo is mandatory in RequestInfo");
            throw new CustomException("USERINFO", "UserInfo is mandatory");
        }
        if (requestInfo.getUserInfo() != null && StringUtils.isBlank(requestInfo.getUserInfo().getUuid())) {
            log.error("UUID is mandatory in UserInfo");
            throw new CustomException("USERINFO_UUID", "UUID is mandatory");
        }
    }

    /* Validates the request data against MDMS data */
    private void  validateMDMSData(List<Project> projects, Object mdmsData, Map<String, String> errorMap) {
        final String jsonPathForWorksTypeOfProjectList = "$.MdmsRes." + MDMS_WORKS_MODULE_NAME + "." + MASTER_PROJECTTYPE + ".*.code";
        String jsonPathForWorksSubTypeOfProject = "$.MdmsRes." + MDMS_WORKS_MODULE_NAME + "." + MASTER_PROJECTTYPE + ".[?(@.active==true && @.code == '{code}')].projectSubType";
        final String jsonPathForWorksDepartment = "$.MdmsRes." + MDMS_COMMON_MASTERS_MODULE_NAME + "." + MASTER_DEPARTMENT + ".*.code";
        String jsonPathForBeneficiaryOfProject = "$.MdmsRes." + MDMS_WORKS_MODULE_NAME + "." + MASTER_PROJECTTYPE + ".[?(@.active==true && @.code == '{code}')].beneficiary";
        final String jsonPathForTenants = "$.MdmsRes." + MDMS_TENANT_MODULE_NAME + "." + MASTER_TENANTS + ".*";
        final String PLACEHOLDER_CODE = "{code}";

        List<Object> deptRes = null;
        List<Object> beneficiaryTypeRes = null;
        List<Object> typeOfProjectRes = null;
        List<Object> tenantRes = null;
        List<List<Object>> projectSubTypeRes = null;
        try {
            deptRes = JsonPath.read(mdmsData, jsonPathForWorksDepartment);
            typeOfProjectRes = JsonPath.read(mdmsData, jsonPathForWorksTypeOfProjectList);
            tenantRes = JsonPath.read(mdmsData, jsonPathForTenants);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException("JSONPATH_ERROR", "Failed to parse mdms response");
        }

        for (Project project: projects) {
            boolean isProjectPresentInMDMS = true;
            log.info("Validate Project type with MDMS");
            if (!StringUtils.isBlank(project.getProjectType()) && !typeOfProjectRes.contains(project.getProjectType())) {
                isProjectPresentInMDMS = false;
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

            //Verify if project subtype is present for project type
            if (isProjectPresentInMDMS && !StringUtils.isBlank(project.getProjectType())) {
                log.info("Validate Project Subtype with MDMS");
                jsonPathForWorksSubTypeOfProject = jsonPathForWorksSubTypeOfProject.replace(PLACEHOLDER_CODE, project.getProjectType());
                projectSubTypeRes = JsonPath.read(mdmsData, jsonPathForWorksSubTypeOfProject);

                if (!StringUtils.isBlank(project.getProjectSubType()) && !projectSubTypeRes.get(0).contains(project.getProjectSubType())) {
                    log.error("The project subtype : " + project.getProjectSubType() + " is not present in MDMS for project type : " + project.getProjectType());
                    errorMap.put("INVALID_PROJECT_SUB_TYPE", "The project subtype : " + project.getProjectSubType() + " is not present in MDMS for project type : " + project.getProjectType());
                }
            }

            if (project.getTargets() != null) {
                for (Target target: project.getTargets()) {
                    //Verify if beneficiary is present for project type
                    if (isProjectPresentInMDMS && !StringUtils.isBlank(target.getBeneficiaryType())) {
                        log.info("Validate Beneficiary Type with MDMS");
                        jsonPathForBeneficiaryOfProject = jsonPathForBeneficiaryOfProject.replace(PLACEHOLDER_CODE, project.getProjectType());
                        beneficiaryTypeRes = JsonPath.read(mdmsData, jsonPathForBeneficiaryOfProject);

                        if (!StringUtils.isBlank(target.getBeneficiaryType()) && !beneficiaryTypeRes.contains(target.getBeneficiaryType())) {
                            log.error("The beneficiary Type : " + target.getBeneficiaryType() + " is not present in MDMS for project type : " + project.getProjectType());
                            errorMap.put("INVALID_BENEFICIARY_TYPE", "The beneficiary Type : " + target.getBeneficiaryType() + " is not present in MDMS for project type : " + project.getProjectType());
                        }
                    }
                }
            }
        }
    }

    /* Validate Project Request MDMS data */
    private void validateRequestMDMSData(ProjectRequest request, String tenantId, Map<String, String> errorMap) {
        String rootTenantId = tenantId.split("\\.")[0];

        //Get MDMS data using create project request and tenantId
        Object mdmsData = mdmsUtils.mDMSCall(request, rootTenantId);

        validateMDMSData(request.getProjects(), mdmsData,  errorMap);
        log.info("Request data validated with MDMS");
    }

    /* Returns localities in list for all Projects in request body */
    private List<String> getLocationForValidation(List<Project> projects) {
        List<String> localities = new ArrayList<>();
        for (Project project: projects) {
            if (project.getAddress() != null && project.getAddress().getLocality() != null) {
                localities.add(project.getAddress().getLocality().getName());
            }
        }
        return localities;
    }

    /* Validates Locality data with location service */
    private void validateLocation(List<String> locations, String tenantId, RequestInfo requestInfo, Map<String, String> errorMap) {
        if (locations.size() > 0) {
            boundaryUtil.validateBoundaryDetails(locations, tenantId, requestInfo, BOUNDARY_ADMIN_HIERARCHY_CODE);
        }
    }

    /* Validates projects data in update request against projects data fetched from database */
    public void validateUpdateAgainstDB(List<Project> projectsFromRequest, List<Project> projectsFromDB) {
        if (CollectionUtils.isEmpty(projectsFromDB)) {
            log.error("The project records that you are trying to update does not exists in the system");
            throw new CustomException("INVALID_PROJECT_MODIFY", "The records that you are trying to update does not exists in the system");
        }

        for (Project project: projectsFromRequest) {
            Project projectFromDB = projectsFromDB.stream().filter(p -> p.getId().equals(project.getId())).findFirst().orElse(null);

            if (projectFromDB == null) {
                log.error("The project id " + project.getId() + " that you are trying to update does not exists for the project");
                throw new CustomException("INVALID_PROJECT_MODIFY", "The project id " + project.getId() + " that you are trying to update does not exists for the project");
            }

            Set<String> targetIdsFromDB = projectFromDB.getTargets().stream().map(Target:: getId).collect(Collectors.toSet());
            if (project.getTargets() != null) {
                for (Target target: project.getTargets()) {
                    if (StringUtils.isNotBlank(target.getId()) && !targetIdsFromDB.contains(target.getId())) {
                        log.error("The target id " + target.getId() + " that you are trying to update does not exists for the project " + projectFromDB.getProjectNumber());
                        throw new CustomException("INVALID_PROJECT_MODIFY.TARGET", "The target id " + target.getId() + " that you are trying to update does not exists for the project " + projectFromDB.getProjectNumber());
                    }
                }
            }

            Set<String> documentIdsFromDB = projectFromDB.getDocuments().stream().map(Document:: getId).collect(Collectors.toSet());
            if (project.getDocuments() != null) {
                for (Document document: project.getDocuments()) {
                    if (StringUtils.isNotBlank(document.getId()) && !documentIdsFromDB.contains(document.getId())) {
                        log.error("The document id " + document.getId() + " that you are trying to update does not exists for the project " + projectFromDB.getProjectNumber());
                        throw new CustomException("INVALID_PROJECT_MODIFY.DOCUMENT", "The document id " + document.getId() + " that you are trying to update does not exists for the project " + projectFromDB.getProjectNumber());
                    }
                }
            }

            if (projectFromDB.getAddress() != null && StringUtils.isBlank(project.getAddress().getId())) {
                log.error("The address with id " + projectFromDB.getAddress().getId() + " already exists for the project");
                throw new CustomException("INVALID_PROJECT_MODIFY.ADDRESS", "The address with id " + projectFromDB.getAddress().getId() + " already exists for the project " + projectFromDB.getProjectNumber());
            }

            if (projectFromDB.getAddress() != null && StringUtils.isNotBlank(project.getAddress().getId()) && !projectFromDB.getAddress().getId().equals(project.getAddress().getId())) {
                log.error("The address id " + project.getAddress().getId() + " that you are trying to update does not exists for the project");
                throw new CustomException("INVALID_PROJECT_MODIFY.ADDRESS", "The address id " + project.getAddress().getId() + " that you are trying to update does not exists for the project " + projectFromDB.getProjectNumber());
            }
        }

    }

    /* Validates if all Projects have same tenant Id */
    private void validateMultipleTenantIds(ProjectRequest projectRequest) {
        List<Project> projects = projectRequest.getProjects();
        String firstTenantId = projects.get(0).getTenantId();
        if (projects.stream().anyMatch(p -> !p.getTenantId().equals(firstTenantId))) {
            log.error("All projects in Project request must have same tenant Id");
            throw new CustomException("MULTIPLE_TENANTS", "All projects must have same tenant Id. Please create new request for different tentant id");
        }
    }

    /* Validate document Ids */
    private void validateDocumentIds(ProjectRequest projectRequest) {
        if ("TRUE".equalsIgnoreCase(config.getDocumentIdVerificationRequired())) {
            //TODO
            // For now throwing exception. Later implementation will be done
            log.error("Document service not integrated yet");
            throw new CustomException("SERVICE_UNAVAILABLE", "Service not integrated yet");
        }
    }

    /* Validates parent data in create request against projects data fetched from database */
    public void validateParentAgainstDB(List<Project> projects, List<Project> parentProjects) {
        Set<String> parentProjectIds = parentProjects.stream().map(Project :: getId).collect(Collectors.toSet());
        for (Project project: projects) {
            if (StringUtils.isNotBlank(project.getParent()) && !parentProjectIds.contains(project.getParent())) {
                log.error("The parent project with id " + project.getParent() + " does not exists in the system");
                throw new CustomException("INVALID_PARENT_PROJECT", "The parent project with id " + project.getParent() + " does not exists in the system");
            }
        }
        log.info("Parent projects validated against DB");
    }
}
