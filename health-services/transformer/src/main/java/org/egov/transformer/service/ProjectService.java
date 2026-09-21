package org.egov.transformer.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectRequest;
import org.egov.common.models.project.ProjectResponse;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.config.TransformerProperties;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.transformer.models.boundary.BoundarySearchResponse;
import org.egov.transformer.models.boundary.EnrichedBoundary;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.transformer.Constants.INTERNAL_SERVER_ERROR;
import static org.egov.transformer.Constants.MDMS_RESPONSE;
import static org.egov.transformer.Constants.PROJECT_TYPES;

@Component
@Slf4j
public class ProjectService {

    private final TransformerProperties transformerProperties;

    private final ServiceRequestClient serviceRequestClient;

    private final ObjectMapper objectMapper;

    private final MdmsService mdmsService;

    private static final Map<String, Project> projectMap = new ConcurrentHashMap<>();

    public ProjectService(TransformerProperties transformerProperties,
                          ServiceRequestClient serviceRequestClient,
                          ObjectMapper objectMapper, MdmsService mdmsService) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.objectMapper = objectMapper;
        this.mdmsService = mdmsService;
    }


    public void updateProjectsInCache(ProjectRequest projectRequest) {
        projectRequest.getProjects().forEach(project -> projectMap.put(project.getId(), project));
    }

    public Project getProject(String projectId, String tenantId) {
        if (projectMap.containsKey(projectId)) {
            log.info("getting project {} from cache", projectId);
            return projectMap.get(projectId);
        }
        List<Project> projects = searchProject(projectId, tenantId);
        Project project = null;
        if (!projects.isEmpty()) {
            project = projects.get(0);
            projectMap.put(projectId, project);
        }
        return project;
    }

    public Project getProjectByName(String projectName, String tenantId) {
        if (projectMap.containsKey(projectName)) {
            log.info("getting project {} from cache", projectName);
            return projectMap.get(projectName);
        }
        List<Project> projects = searchProjectByName(projectName, tenantId);
        Project project = null;
        if (!projects.isEmpty()) {
            project = projects.get(0);
            projectMap.put(projectName, project);
        }
        return project;
    }

    public Map<String, String> getBoundaryCodeToNameMapByProjectId(String projectId, String tenantId) {
        Project project = getProject(projectId, tenantId);
        String locationCode = project.getAddress().getBoundary();
        return getBoundaryCodeToNameMap(locationCode, tenantId);
    }

    public Map<String, String> getBoundaryCodeToNameMap(String locationCode, String tenantId) {
        List<EnrichedBoundary> boundaries = new ArrayList<>();
        try {
            // Fetch boundary details from the service
            log.debug("Fetching boundary relation details for tenantId: {}, boundary: {}", tenantId, locationCode);
            BoundarySearchResponse boundarySearchResponse = serviceRequestClient.fetchResult(
                    new StringBuilder(transformerProperties.getBoundaryServiceHost()
                            + transformerProperties.getBoundaryRelationshipSearchUrl()
                            +"?includeParents=true&tenantId=" + tenantId
                            + "&hierarchyType=" + transformerProperties.getBoundaryHierarchyName()
                            + "&codes=" + locationCode),
                    RequestInfo.builder().build(),
                    BoundarySearchResponse.class
            );
            log.debug("Boundary Relationship details fetched successfully for tenantId: {}", tenantId);

            List<EnrichedBoundary> enrichedBoundaries = boundarySearchResponse.getTenantBoundary().stream()
                    .filter(hierarchyRelation -> !CollectionUtils.isEmpty(hierarchyRelation.getBoundary()))
                    .flatMap(hierarchyRelation -> hierarchyRelation.getBoundary().stream())
                    .collect(Collectors.toList());

            getAllBoundaryCodes(enrichedBoundaries, boundaries);

        } catch (Exception e) {
            log.error("Exception while searching boundaries for tenantId: {}", tenantId, e);
            // Throw a custom exception if an error occurs during boundary search
            throw new CustomException("BOUNDARY_SERVICE_SEARCH_ERROR","Error in while fetching boundaries from Boundary Service : " + e.getMessage());
        }

        return boundaries.stream()
                .collect(Collectors.toMap(
                        EnrichedBoundary::getBoundaryType,
                        boundary -> boundary.getCode().substring(boundary.getCode().lastIndexOf('_') + 1)
                ));
    }

    private void getAllBoundaryCodes(List<EnrichedBoundary> enrichedBoundaries, List<EnrichedBoundary> boundaries) {
        if (enrichedBoundaries == null || enrichedBoundaries.isEmpty()) {
            return;
        }

        for (EnrichedBoundary root : enrichedBoundaries) {
            if (root != null) {
                Deque<EnrichedBoundary> stack = new ArrayDeque<>();
                stack.push(root);

                while (!stack.isEmpty()) {
                    EnrichedBoundary current = stack.pop();
                    if (current != null) {
                        boundaries.add(current);
                        if (current.getChildren() != null) {
                            stack.addAll(current.getChildren());
                        }
                    }
                }
            }
        }
    }



    private List<Project> searchProjectByName(String projectName, String tenantId) {

        ProjectRequest request = ProjectRequest.builder()
                .requestInfo(RequestInfo.builder().
                        userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .projects(Collections.singletonList(Project.builder().name(projectName).tenantId(tenantId).build()))
                .build();

        try {
            log.info(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        ProjectResponse response;
        try {
            StringBuilder uri = new StringBuilder();
            uri.append(transformerProperties.getProjectHost())
                    .append(transformerProperties.getProjectSearchUrl())
                    .append("?limit=").append(transformerProperties.getSearchApiLimit())
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);
            response = serviceRequestClient.fetchResult(uri,
                    request,
                    ProjectResponse.class);
        } catch (Exception e) {
            log.error("error while fetching project list", e);
            throw new CustomException("PROJECT_FETCH_ERROR",
                    "error while fetching project details for name: " + projectName);
        }
        return response.getProject();
    }

    private List<Project> searchProject(String projectId, String tenantId) {

        ProjectRequest request = ProjectRequest.builder()
                .requestInfo(RequestInfo.builder().
                userInfo(User.builder()
                        .uuid("transformer-uuid")
                        .build())
                .build())
                .projects(Collections.singletonList(Project.builder().id(projectId).tenantId(tenantId).build()))
                .build();

        ProjectResponse response;
        try {
            StringBuilder uri = new StringBuilder();
            uri.append(transformerProperties.getProjectHost())
                    .append(transformerProperties.getProjectSearchUrl())
                    .append("?limit=").append(transformerProperties.getSearchApiLimit())
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);
            response = serviceRequestClient.fetchResult(uri,
                    request,
                    ProjectResponse.class);
        } catch (Exception e) {
            log.error("error while fetching project list", e);
            throw new CustomException("PROJECT_FETCH_ERROR",
                    "error while fetching project details for id: " + projectId);
        }
        return response.getProject();
    }

    public List<String> getProducts(String tenantId, String projectTypeId) {
        String filter = "$[?(@.id == '" + projectTypeId + "')].resources.*.productVariantId";

        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();

        JsonNode response = fetchMdmsResponse(requestInfo, tenantId, PROJECT_TYPES,
                transformerProperties.getMdmsModule(), filter);
        return convertToProjectTypeList(response);
    }

    private JsonNode fetchMdmsResponse(RequestInfo requestInfo, String tenantId, String name,
                                       String moduleName, String filter) {
        MdmsCriteriaReq serviceRegistry = getMdmsRequest(requestInfo, tenantId, name, moduleName, filter);
        try {
            return mdmsService.fetchConfig(serviceRegistry, JsonNode.class).get(MDMS_RESPONSE);
        } catch (Exception e) {
            throw new CustomException(INTERNAL_SERVER_ERROR, "Error while fetching mdms config");
        }
    }

    private List<String> convertToProjectTypeList(JsonNode jsonNode) {
        JsonNode projectTypesNode = jsonNode.get(transformerProperties.getMdmsModule()).withArray(PROJECT_TYPES);
        return new ObjectMapper().convertValue(projectTypesNode, new TypeReference<List<String>>() {
        });
    }

    private MdmsCriteriaReq getMdmsRequest(RequestInfo requestInfo, String tenantId, String masterName,
                                           String moduleName, String filter) {
        MasterDetail masterDetail = new MasterDetail();
        masterDetail.setName(masterName);
        masterDetail.setFilter(filter);
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
}
