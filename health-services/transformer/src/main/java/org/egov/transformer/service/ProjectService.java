package org.egov.transformer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.project.*;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.models.downstream.ProjectInfo;
import org.egov.transformer.producer.TransformerErrorProducer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.egov.transformer.models.boundary.*;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.egov.transformer.Constants.*;

@Component
@Slf4j
public class ProjectService {

    private final TransformerProperties transformerProperties;
    private final ServiceRequestClient serviceRequestClient;
    private final ObjectMapper objectMapper;
    private final MdmsService mdmsService;
    private final TransformerErrorProducer errorProducer;
    private final ProjectFactoryService projectFactoryService;
    private final CommonUtils commonUtils;

    private static Map<String, String> projectTypeIdVsProjectBeneficiaryCache = new ConcurrentHashMap<>();
    private static Map<String, ProjectInfo> projectIdVsProjectInfoCache = new ConcurrentHashMap<>();
    private static Map<String, String> userIdVsProjectIdCache = new ConcurrentHashMap<>();
    private static Map<String, ArrayNode> projectIdVsCycleInfoCache = new ConcurrentHashMap<>();


    public ProjectService(TransformerProperties transformerProperties,
                          ServiceRequestClient serviceRequestClient,
                          ObjectMapper objectMapper, MdmsService mdmsService, TransformerErrorProducer errorProducer, ProjectFactoryService projectFactoryService, CommonUtils commonUtils) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.objectMapper = objectMapper;
        this.mdmsService = mdmsService;
        this.errorProducer = errorProducer;
        this.projectFactoryService = projectFactoryService;
        this.commonUtils = commonUtils;
    }

    public Project getProject(String projectId, String tenantId) {
        List<Project> projects = searchProject(projectId, tenantId);
        Project project = null;
        if (projects != null && !projects.isEmpty()) {
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

    public String fetchCycleIndexFromProjectAdditionalDetails(String tenantId, String projectId, String projectTypeId, Long createdTime) {
        if (projectId == null && projectTypeId == null) {
            return null;
        }

        ArrayNode cachedCycles = projectIdVsCycleInfoCache.get(projectId);
        if (cachedCycles != null) {
            return commonUtils.findCycleIndex(cachedCycles, createdTime);
        }

        Project project = getProject(projectId, tenantId);
        if (project == null) {
            return null;
        }

        JsonNode additionalDetails = objectMapper.valueToTree(project.getAdditionalDetails());
        if (additionalDetails == null || additionalDetails.isMissingNode()) {
            return null;
        }

        JsonNode projectTypeNode = additionalDetails.path("projectType");
        if (projectTypeNode.isMissingNode() || projectTypeNode.isNull()) {
            return commonUtils.fetchCycleIndexFromTime(tenantId, projectTypeId, createdTime);
        }

        ArrayNode campCycles = objectMapper.createArrayNode();
        JsonNode cyclesNode = projectTypeNode.path("cycles");

        if (!cyclesNode.isArray() || cyclesNode.isEmpty()) {
            projectIdVsCycleInfoCache.put(projectId, campCycles);
            return null;
        }

        for (JsonNode cycle : cyclesNode) {
            if (!cycle.has("id") || !cycle.has("startDate") || !cycle.has("endDate")) {
                continue;
            }

            ObjectNode normalized = objectMapper.createObjectNode();
            normalized.put("id", cycle.path("id").asInt(0));
            normalized.put(START_DATE, cycle.path("startDate").asLong(0));
            normalized.put(END_DATE, cycle.path("endDate").asLong(0));

            campCycles.add(normalized);
        }

        if (campCycles.isEmpty()) {
            return null;
        }
        projectIdVsCycleInfoCache.put(projectId, campCycles);
        return commonUtils.findCycleIndex(campCycles, createdTime);
    }

    public ProjectInfo getProjectInfoByProjectId(String projectId, String tenantId) {
        ProjectInfo cachedProjectInfo = projectIdVsProjectInfoCache.get(projectId);
        if (cachedProjectInfo != null) {
            log.info("Fetched ProjectInfo from cache for project id: {}", projectId);
            if (cachedProjectInfo.getCampaignId() == null
                    && StringUtils.isNotBlank(cachedProjectInfo.getCampaignNumber())) {
                cachedProjectInfo.setCampaignId(projectFactoryService.getCampaignIdFromCampaignNumber(
                        tenantId, true, cachedProjectInfo.getCampaignNumber()));
            }
            return cachedProjectInfo;
        }

        Project project = getProject(projectId, tenantId);
        if (project == null) {
            log.info("No project found for project id: {}. Not caching.", projectId);
            return new ProjectInfo();
        }

        ProjectInfo projectInfo = new ProjectInfo();
        projectInfo.setProjectType(project.getProjectType());
        projectInfo.setProjectTypeId(project.getProjectTypeId());
        projectInfo.setProjectId(project.getId());
        projectInfo.setProjectName(project.getName());
        projectInfo.setCampaignNumber(project.getReferenceID());
        projectInfo.setHierarchyType(getHierarchyTypeFromProject(project));
        if (StringUtils.isNotBlank(project.getReferenceID())) {
            projectInfo.setCampaignId(projectFactoryService.getCampaignIdFromCampaignNumber(
                    tenantId, true, project.getReferenceID()));
        }
        projectIdVsProjectInfoCache.put(projectId, projectInfo);
        return projectInfo;
    }

    public String getHierarchyTypeFromProject(Project project) {
        try {
            JsonNode additionalDetails = objectMapper.valueToTree(project.getAdditionalDetails());
            if (additionalDetails != null && !additionalDetails.isMissingNode()
                    && additionalDetails.hasNonNull("hierarchyType")) {
                String hierarchyType = additionalDetails.get("hierarchyType").asText(null);
                if (StringUtils.isNotBlank(hierarchyType)) {
                    log.info("hierarchyType resolved from project additionalDetails for projectId: {}, hierarchyType: {}", project.getId(), hierarchyType);
                    return hierarchyType;
                }
                log.info("hierarchyType present but blank in project additionalDetails for projectId: {}, falling back to configured default: {}", project.getId(), transformerProperties.getBoundaryHierarchyName());
            } else {
                log.info("hierarchyType not present in project additionalDetails for projectId: {}, falling back to configured default: {}", project.getId(), transformerProperties.getBoundaryHierarchyName());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch hierarchyType from project additionalDetails for projectId: {}, falling back to configured default: {}", project.getId(), transformerProperties.getBoundaryHierarchyName());
        }
        return transformerProperties.getBoundaryHierarchyName();
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
            log.error("error while serializing project request for name: {}, Exception: {}", projectName, ExceptionUtils.getStackTrace(e));
            // no emit here: the exception propagates to the consumer, which records a single
            // error with the correct source topic and original payload.
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
            // no emit here: the exception propagates to the consumer, which records a single
            // error with the correct source topic and original payload.
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
            log.error("error while fetching project list for ID {}, Exception: {}", projectId, ExceptionUtils.getStackTrace(e));
            errorProducer.sendToErrorTopic(request, null, e);
            return null;
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
            log.error("error while fetching beneficiary for id: {}, Exception: {}", projectBeneficiaryClientRefId, ExceptionUtils.getStackTrace(e));
            errorProducer.sendToErrorTopic(request, null, e);
            return Collections.emptyList();
        }
        return response.getProjectBeneficiaries();
    }

    public ProjectInfo projectDetailsFromUserId(String userId, String tenantId){
        if (userIdVsProjectIdCache.containsKey(userId)) {
            return getProjectInfoByProjectId(userIdVsProjectIdCache.get(userId), tenantId);
        }

        List<String> userIds = new ArrayList<>(Collections.singletonList(userId));
        ProjectInfo projectInfo = new ProjectInfo();
        List<ProjectStaff> projectStaffList = searchProjectStaff(userIds, tenantId);
        ProjectStaff projectStaff = !CollectionUtils.isEmpty(projectStaffList) ? projectStaffList.get(0) : null;

        if (ObjectUtils.isNotEmpty(projectStaff)) {
            projectInfo = getProjectInfoByProjectId(projectStaff.getProjectId(), tenantId);
            userIdVsProjectIdCache.put(userId, projectStaff.getProjectId());
        }
        return projectInfo;
    }

    public void addProjectDetailsForUserIdAndTenantId(ProjectInfo projectInfo, String userId, String tenantId) {
        ProjectInfo projectDetails = projectDetailsFromUserId(userId, tenantId);
        if(ObjectUtils.isNotEmpty(projectDetails)) {
            projectInfo.setProjectId(projectDetails.getProjectId());
            projectInfo.setProjectTypeId(projectDetails.getProjectTypeId());
            projectInfo.setProjectType(projectDetails.getProjectType());
            projectInfo.setProjectName(projectDetails.getProjectName());
            projectInfo.setCampaignNumber(projectDetails.getCampaignNumber());
            projectInfo.setCampaignId(projectDetails.getCampaignId());
            projectInfo.setHierarchyType(projectDetails.getHierarchyType());
        }
    }

//    TODO getProducts from projectAdditionalDetails instead of mdms projectType
    public List<String> getProducts(String tenantId, String projectTypeId) {
        String filter = "$[?(@.id == '" + projectTypeId + "')].resources.*.productVariantId";

        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();

        JsonNode response = mdmsService.fetchMdmsResponse(requestInfo, tenantId, PROJECT_TYPES,
                transformerProperties.getMdmsModule(), filter);
        JsonNode projectTypesNode = response.get(transformerProperties.getMdmsModule()).withArray(PROJECT_TYPES);
        return new ObjectMapper().convertValue(projectTypesNode, new TypeReference<List<String>>() {
        });
    }

    public String getProjectBeneficiaryType(String tenantId, String projectTypeId) {
        if (projectTypeIdVsProjectBeneficiaryCache.containsKey(projectTypeId)) {
            return projectTypeIdVsProjectBeneficiaryCache.get(projectTypeId);
        }
        String filter = "$[?(@.id == '" + projectTypeId + "')].beneficiaryType";
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();
        try {
            JsonNode response = mdmsService.fetchMdmsResponse(requestInfo, tenantId, PROJECT_TYPES,
                    transformerProperties.getMdmsModule(), filter);

            if (response != null && response.has(transformerProperties.getMdmsModule())) {
                JsonNode projectBeneficiaryTypeNode = response
                        .get(transformerProperties.getMdmsModule())
                        .withArray(PROJECT_TYPES);

                if (projectBeneficiaryTypeNode != null && projectBeneficiaryTypeNode.isArray() && !projectBeneficiaryTypeNode.isEmpty()) {
                    String projectBeneficiaryType = projectBeneficiaryTypeNode.get(0).asText();
                    projectTypeIdVsProjectBeneficiaryCache.put(projectTypeId, projectBeneficiaryType);
                    return projectBeneficiaryType;
                }
            }
        } catch (Exception exception) {
            log.error("error while fetching projectBeneficiaryType from MDMS for projectTypeId: {}. ExceptionDetails {}", projectTypeId, ExceptionUtils.getStackTrace(exception));
            errorProducer.sendToErrorTopic(projectTypeId, null, exception);
        }
        return null;
    }

    public JsonNode fetchProjectAdditionalDetails(Project project) {
        JsonNode projectAdditionalDetails = objectMapper.valueToTree(project.getAdditionalDetails());
        if (projectAdditionalDetails == null || projectAdditionalDetails.isEmpty() || !projectAdditionalDetails.has(PROJECT_TYPE)) {
            return null;
        }
        JsonNode projectType = projectAdditionalDetails.get(PROJECT_TYPE);
        if (projectType.has(CYCLES) && !projectType.get(CYCLES).isEmpty()) {
            return extractProjectCycleAndDoseIndexes(projectType);
        }
        return null;
    }

    private JsonNode extractProjectCycleAndDoseIndexes(JsonNode projectType) {
        ArrayNode cycles = (ArrayNode) projectType.get(CYCLES);
        ArrayNode doseIndex = JsonNodeFactory.instance.arrayNode();
        ArrayNode cycleIndex = JsonNodeFactory.instance.arrayNode();
        // Adding 0 as prefix here because we are sending cycle and dose as 01, 02 strings from app
        // due to character length limit on additionalField values,
        // for dashboard controls we are converting here so that filters get applied properly between multiple indexes
        try {
            cycles.forEach(cycle -> {
                if (cycle.has(ID)) {
                    cycleIndex.add(PREFIX_ZERO + cycle.get(ID).asText());
                }
            });
            ArrayNode deliveries = (ArrayNode) cycles.get(0).get(DELIVERIES);
            deliveries.forEach(delivery -> {
                if (delivery.has(ID)) {
                    doseIndex.add(PREFIX_ZERO + delivery.get(ID).asText());
                }
            });

            ObjectNode result = JsonNodeFactory.instance.objectNode();
            result.set(DOSE_INDEX, doseIndex);
            result.set(CYCLE_INDEX, cycleIndex);
            return result;
        } catch (Exception e) {
            log.error("Error while extracting cycle and dose indexes from projectType: {}", ExceptionUtils.getStackTrace(e));
            errorProducer.sendToErrorTopic(projectType, null, e);
            return null;
        }
    }

    public String getProjectIdFromStaff(String userId, String tenantId) {
        if (userIdVsProjectIdCache.containsKey(userId)) {
            return userIdVsProjectIdCache.get(userId);
        }

        List<String> userIds = new ArrayList<>(Collections.singletonList(userId));
        List<ProjectStaff> projectStaffList = searchProjectStaff(userIds, tenantId);
        ProjectStaff projectStaff = !CollectionUtils.isEmpty(projectStaffList) ? projectStaffList.get(0) : null;

        if (ObjectUtils.isNotEmpty(projectStaff)) {
            userIdVsProjectIdCache.put(userId, projectStaff.getProjectId());
            return projectStaff.getProjectId();
        }
        return null;
    }
    private List<ProjectStaff> searchProjectStaff(List<String> userId, String tenantId) {
        ProjectStaffSearchRequest request = ProjectStaffSearchRequest.builder()
                .requestInfo(RequestInfo.builder()
                        .userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .projectStaff(ProjectStaffSearch.builder().staffId(userId).tenantId(tenantId).build())
                .build();

        try {
            StringBuilder uri = new StringBuilder();
            uri.append(transformerProperties.getProjectHost())
                    .append(transformerProperties.getProjectStaffSearchUrl())
                    .append("?limit=").append(transformerProperties.getSearchApiLimit())
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);
            ProjectStaffBulkResponse response = serviceRequestClient.fetchResult(uri,
                    request,
                    ProjectStaffBulkResponse.class);
            return !response.getProjectStaff().isEmpty() ? response.getProjectStaff() : null;
        } catch (Exception e) {
            log.error("Error while fetching project staff list {}", ExceptionUtils.getStackTrace(e));
            errorProducer.sendToErrorTopic(request, null, e);
            return null;
        }
    }

}
