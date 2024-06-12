package org.egov.transformer.service;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.project.BeneficiaryBulkResponse;
import org.egov.common.models.project.BeneficiarySearchRequest;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.ProjectBeneficiarySearch;
import org.egov.common.models.project.ProjectRequest;
import org.egov.common.models.project.ProjectResponse;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkResponse;
import org.egov.common.models.project.ProjectStaffSearch;
import org.egov.common.models.project.ProjectStaffSearchRequest;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.models.boundary.BoundarySearchResponse;
import org.egov.transformer.models.boundary.EnrichedBoundary;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.transformer.Constants.CYCLES;
import static org.egov.transformer.Constants.CYCLE_NUMBER;
import static org.egov.transformer.Constants.DELIVERIES;
import static org.egov.transformer.Constants.DOSE_NUMBER;
import static org.egov.transformer.Constants.ID;
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


    public ProjectService(TransformerProperties transformerProperties,
                          ServiceRequestClient serviceRequestClient,
                          ObjectMapper objectMapper, MdmsService mdmsService) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.objectMapper = objectMapper;
        this.mdmsService = mdmsService;
    }

    public Project getProject(String projectId, String tenantId) {
        List<Project> projects = searchProject(projectId, tenantId);
        Project project = null;
        if (!projects.isEmpty()) {
            project = projects.get(0);
        }
        return project;
    }

    public Project getProjectByName(String projectName, String tenantId) {
        List<Project> projects = searchProjectByName(projectName, tenantId);
        Project project = null;
        if (!projects.isEmpty()) {
            project = projects.get(0);
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
            throw new CustomException("BOUNDARY_SEARCH_ERROR", e.getMessage());
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
            log.error("error while fetching project list {}", ExceptionUtils.getStackTrace(e));
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
            log.error("error while fetching project list {}", ExceptionUtils.getStackTrace(e));
            throw new CustomException("PROJECT_FETCH_ERROR",
                    "error while fetching project details for id: " + projectId);
        }
        return response.getProject();
    }

    public List<ProjectBeneficiary> searchBeneficiary(String projectBeneficiaryClientRefId, String tenantId) {
        BeneficiarySearchRequest request = BeneficiarySearchRequest.builder()
                .requestInfo(RequestInfo.builder().
                        userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .projectBeneficiary(ProjectBeneficiarySearch.builder().
                        clientReferenceId(Collections.singletonList(projectBeneficiaryClientRefId)).build())
                .build();
        BeneficiaryBulkResponse response;
        try {
            StringBuilder uri = new StringBuilder();
            uri.append(transformerProperties.getProjectHost())
                    .append(transformerProperties.getProjectBeneficiarySearchUrl())
                    .append("?limit=").append(transformerProperties.getSearchApiLimit())
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);
            response = serviceRequestClient.fetchResult(uri,
                    request,
                    BeneficiaryBulkResponse.class);
        } catch (Exception e) {
            log.error("error while fetching beneficiary {}", ExceptionUtils.getStackTrace(e));
            throw new CustomException("PROJECT_BENEFICIARY_FETCH_ERROR",
                    "error while fetching beneficiary details for id: " + projectBeneficiaryClientRefId);
        }
        return response.getProjectBeneficiaries();
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

    public String getProjectBeneficiaryType(String tenantId, String projectTypeId) {
        String filter = "$[?(@.id == '" + projectTypeId + "')].beneficiaryType";
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();
        try {
            JsonNode response = fetchMdmsResponse(requestInfo, tenantId, PROJECT_TYPES,
                    transformerProperties.getMdmsModule(), filter);

            if (response != null && response.has(transformerProperties.getMdmsModule())) {
                JsonNode projectBeneficiaryTypeNode = response
                        .get(transformerProperties.getMdmsModule())
                        .withArray(PROJECT_TYPES);

                if (projectBeneficiaryTypeNode != null && projectBeneficiaryTypeNode.isArray() && projectBeneficiaryTypeNode.size() > 0) {
                    return projectBeneficiaryTypeNode.get(0).asText();
                }
            }
        } catch (Exception exception) {
            log.error("error while fetching projectBeneficiaryType from MDMS for projectTypeId: {}. ExceptionDetails {}", projectTypeId, ExceptionUtils.getStackTrace(exception));
        }
        return null;
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

    public JsonNode fetchBoundaryData(String tenantId, String filter, String projectTypeId) {
        List<JsonNode> projectTypes = new ArrayList<>();
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();
        try {
            JsonNode response = fetchMdmsResponse(requestInfo, tenantId, transformerProperties.getBoundaryHierarchyMaster(),
                    transformerProperties.getBoundaryHierarchyModule(), filter);
            projectTypes = convertToProjectTypeJsonNodeListv2(response);
            JsonNode requiredProjectType = projectTypes.stream().filter(projectType -> projectType.get(Constants.ID).asText().equals(projectTypeId)).findFirst().get();
            return requiredProjectType.get(Constants.BOUNDARY_DATA);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public JsonNode fetchBoundaryDataByTenant(String tenantId, String filter) {
        List<JsonNode> projectTypes = new ArrayList<>();
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();
        try {
            JsonNode response = fetchMdmsResponse(requestInfo, tenantId, transformerProperties.getBoundaryHierarchyMaster(),
                    transformerProperties.getBoundaryHierarchyModule(), filter);
            projectTypes = convertToProjectTypeJsonNodeListv2(response);
            for (JsonNode projectType : projectTypes) {
                JsonNode boundaryData = projectType.get(Constants.BOUNDARY_DATA);
                if (boundaryData != null) {
                    return boundaryData;
                }
            }
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public JsonNode fetchProjectTypes(String tenantId, String filter, String projectTypeId) {
        List<JsonNode> projectTypes = new ArrayList<>();
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();
        try {
            JsonNode response = fetchMdmsResponse(requestInfo, tenantId, PROJECT_TYPES, transformerProperties.getMdmsModule(), filter);
            projectTypes = convertToProjectTypeJsonNodeList(response);
            JsonNode requiredProjectType = projectTypes.stream().filter(projectType -> projectType.get(Constants.ID).asText().equals(projectTypeId)).findFirst().get();
            return requiredProjectType;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public JsonNode fetchAdditionalDetails(String tenantId, String filter, String projectTypeId) {

        JsonNode additionalDetails = null;
        JsonNode requiredProjectType = fetchProjectTypes(tenantId, filter, projectTypeId);
        if (requiredProjectType.has(CYCLES) && !requiredProjectType.get(CYCLES).isEmpty()) {
            additionalDetails = extractCycleAndDoseIndexes(requiredProjectType);
        }
        return additionalDetails;
    }

    private JsonNode extractCycleAndDoseIndexes(JsonNode projectType) {
        ArrayNode cycles = (ArrayNode) projectType.get(CYCLES);
        ArrayNode doseIndex = JsonNodeFactory.instance.arrayNode();
        ArrayNode cycleIndex = JsonNodeFactory.instance.arrayNode();
        try {
            cycles.forEach(cycle -> {
                if (cycle.has(ID)) {
                    cycleIndex.add(cycle.get(ID).asInt());
                }
            });
            ArrayNode deliveries = (ArrayNode) cycles.get(0).get(DELIVERIES);
            deliveries.forEach(delivery -> {
                if (delivery.has(ID)) {
                    doseIndex.add(delivery.get(ID).asInt());
                }
            });

            ObjectNode result = JsonNodeFactory.instance.objectNode();
            result.set(DOSE_NUMBER, doseIndex);
            result.set(CYCLE_NUMBER, cycleIndex);
            return result;
        } catch (Exception e) {
            log.info("Error while fetching cycle and dose indexes from MDMS: {}", ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    private List<String> convertToProjectTypeList(JsonNode jsonNode) {
        JsonNode projectTypesNode = jsonNode.get(transformerProperties.getMdmsModule()).withArray(PROJECT_TYPES);
        return new ObjectMapper().convertValue(projectTypesNode, new TypeReference<List<String>>() {
        });
    }

    private List<JsonNode> convertToProjectTypeJsonNodeList(JsonNode jsonNode) throws IOException {
        JsonNode projectTypesNode = jsonNode.get(transformerProperties.getMdmsModule()).withArray(PROJECT_TYPES);
        return objectMapper.readValue(projectTypesNode.traverse(), new TypeReference<List<JsonNode>>() {
        });
    }

    private List<JsonNode> convertToProjectTypeJsonNodeListv2(JsonNode jsonNode) throws IOException {
        JsonNode projectTypesNode = jsonNode.get(transformerProperties.getBoundaryHierarchyModule()).withArray(transformerProperties.getBoundaryHierarchyMaster());
        return objectMapper.readValue(projectTypesNode.traverse(), new TypeReference<List<JsonNode>>() {
        });
    }

    private MdmsCriteriaReq getMdmsRequest(RequestInfo requestInfo, String tenantId, String masterName,
                                           String moduleName, String filter) {
        MasterDetail masterDetail = new MasterDetail();
        masterDetail.setName(masterName);
        if (filter != null && !filter.isEmpty()) {
            masterDetail.setFilter(filter);
        }
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

    public ProjectStaff searchProjectStaff(String userId, String tenantId) {

        ProjectStaffSearchRequest request = ProjectStaffSearchRequest.builder()
                .requestInfo(RequestInfo.builder()
                        .userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .projectStaff(ProjectStaffSearch.builder().staffId(Arrays.asList(userId)).tenantId(tenantId).build())
                .build();

        ProjectStaffBulkResponse response;
        try {
            StringBuilder uri = new StringBuilder();
            uri.append(transformerProperties.getProjectHost())
                    .append(transformerProperties.getProjectStaffSearchUrl())
                    .append("?limit=").append(transformerProperties.getSearchApiLimit())
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);
            response = serviceRequestClient.fetchResult(uri,
                    request,
                    ProjectStaffBulkResponse.class);
        } catch (Exception e) {
            log.error("Error while fetching project staff list {}", ExceptionUtils.getStackTrace(e));

            return null;
        }
        return !response.getProjectStaff().isEmpty() ? response.getProjectStaff().get(0) : null;
    }


}
