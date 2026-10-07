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
import java.util.stream.Collectors;

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
    private static Map<String, CachedProjectStaff> userIdVsProjectStaffCache = new ConcurrentHashMap<>();
    private static Map<String, Project> projectIdVsProjectCache = new ConcurrentHashMap<>();
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
        return searchBeneficiary(ProjectBeneficiarySearch.builder()
                .clientReferenceId(Collections.singletonList(projectBeneficiaryClientRefId)).build(), tenantId);
    }

    /**
     * Resolves the project from the project beneficiary registered for the given beneficiary ids, tried in order
     * (individual id for individual-based projects, then household id for household-based projects).
     * Returns null if no beneficiary is found; the user's project-staff mapping is not used as it is ambiguous when
     * the user is staff of more than one project running at the same time.
     */
    public ProjectInfo projectInfoFromBeneficiaryIds(List<String> beneficiaryIds, String tenantId) {
        if (CollectionUtils.isEmpty(beneficiaryIds) || StringUtils.isBlank(tenantId)) {
            return null;
        }
        for (String beneficiaryId : beneficiaryIds) {
            if (StringUtils.isBlank(beneficiaryId)) {
                continue;
            }
            List<ProjectBeneficiary> beneficiaries = searchBeneficiary(ProjectBeneficiarySearch.builder()
                    .beneficiaryId(beneficiaryId).build(), tenantId);
            // the same beneficiary can be registered in more than one project; take the latest registration
            ProjectBeneficiary beneficiary = beneficiaries.stream()
                    .filter(b -> b != null && StringUtils.isNotBlank(b.getProjectId()) && !Boolean.TRUE.equals(b.getIsDeleted()))
                    .max(Comparator.comparing(b -> b.getAuditDetails() != null && b.getAuditDetails().getCreatedTime() != null
                            ? b.getAuditDetails().getCreatedTime() : 0L))
                    .orElse(null);
            if (beneficiary == null) {
                continue;
            }
            if (beneficiaries.size() > 1) {
                log.warn("Multiple project beneficiaries for beneficiaryId {}, using project {}", beneficiaryId, beneficiary.getProjectId());
            }
            ProjectInfo projectInfo = getProjectInfoByProjectId(beneficiary.getProjectId(), tenantId);
            if (projectInfo != null && StringUtils.isNotBlank(projectInfo.getProjectId())) {
                return projectInfo;
            }
            log.warn("Project {} of beneficiaryId {} not found", beneficiary.getProjectId(), beneficiaryId);
        }
        return null;
    }

    private List<ProjectBeneficiary> searchBeneficiary(ProjectBeneficiarySearch projectBeneficiarySearch, String tenantId) {
        BeneficiarySearchRequest request = BeneficiarySearchRequest.builder()
                .requestInfo(RequestInfo.builder().
                        userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .projectBeneficiary(projectBeneficiarySearch)
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
            log.error("error while fetching beneficiary for search: {}, Exception: {}", projectBeneficiarySearch, ExceptionUtils.getStackTrace(e));
            errorProducer.sendToErrorTopic(request, null, e);
            return Collections.emptyList();
        }
        return response != null && response.getProjectBeneficiaries() != null
                ? response.getProjectBeneficiaries() : Collections.emptyList();
    }

    public ProjectInfo projectDetailsFromUserId(String userId, String tenantId) {
        return projectDetailsFromUserId(userId, tenantId, null);
    }

    /**
     * Resolves the project of the record from the user's project-staff mapping. eventTime is when the record was
     * captured, used to pick the right project when the user is staff of more than one project; when null the
     * current time is used.
     */
    public ProjectInfo projectDetailsFromUserId(String userId, String tenantId, Long eventTime) {
        String projectId = getProjectIdFromStaff(userId, tenantId, eventTime);
        return projectId != null ? getProjectInfoByProjectId(projectId, tenantId) : new ProjectInfo();
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
        return getProjectIdFromStaff(userId, tenantId, null);
    }

    /**
     * A user can be staff of more than one project (mapped to more than one campaign), so the first staff record
     * returned by the search is not necessarily the project the record belongs to. The project whose staff mapping
     * and project dates cover eventTime is used; if more than one does, the most recently started project.
     */
    public String getProjectIdFromStaff(String userId, String tenantId, Long eventTime) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(tenantId)) {
            return null;
        }
        List<ProjectStaff> projectStaffList = getProjectStaff(userId, tenantId);
        if (CollectionUtils.isEmpty(projectStaffList)) {
            return null;
        }
        Set<String> projectIds = new LinkedHashSet<>();
        projectStaffList.forEach(projectStaff -> projectIds.add(projectStaff.getProjectId()));
        if (projectIds.size() == 1) {
            return projectIds.iterator().next();
        }

        long time = eventTime != null && eventTime > 0 ? eventTime : System.currentTimeMillis();
        Map<String, Project> projects = new HashMap<>();
        projectIds.forEach(projectId -> projects.put(projectId, getProjectCached(projectId, tenantId)));
        Comparator<ProjectStaff> latestStarted = Comparator
                .comparing((ProjectStaff projectStaff) -> projectStartDate(projects.get(projectStaff.getProjectId())))
                .thenComparing(projectStaff -> projectStaff.getAuditDetails() != null && projectStaff.getAuditDetails().getCreatedTime() != null
                        ? projectStaff.getAuditDetails().getCreatedTime() : 0L);

        List<ProjectStaff> activeAtTime = projectStaffList.stream()
                .filter(projectStaff -> isWithin(time, projectStaff.getStartDate(), projectStaff.getEndDate()))
                .filter(projectStaff -> {
                    Project project = projects.get(projectStaff.getProjectId());
                    return project != null && isWithin(time, project.getStartDate(), project.getEndDate());
                })
                .collect(Collectors.toList());
        ProjectStaff selected;
        if (!activeAtTime.isEmpty()) {
            selected = Collections.max(activeAtTime, latestStarted);
            if (activeAtTime.size() > 1) {
                log.warn("User {} is staff of {} projects active at {}, using the latest started project {}",
                        userId, activeAtTime.size(), time, selected.getProjectId());
            }
        } else {
            // no project running at that time; take the latest project started before it, else the latest one
            selected = projectStaffList.stream()
                    .filter(projectStaff -> projectStartDate(projects.get(projectStaff.getProjectId())) <= time)
                    .max(latestStarted)
                    .orElse(Collections.max(projectStaffList, latestStarted));
            log.warn("User {} is staff of projects {}, none active at {}, using project {}",
                    userId, projectIds, time, selected.getProjectId());
        }
        log.info("User {} is staff of projects {}, resolved project {} for time {}", userId, projectIds, selected.getProjectId(), time);
        return selected.getProjectId();
    }

    /**
     * Drops the cached project-staff mappings of the user so that a new or updated mapping is picked up.
     */
    public void evictProjectStaffCache(String userId, String tenantId) {
        if (StringUtils.isNotBlank(userId) && StringUtils.isNotBlank(tenantId)) {
            userIdVsProjectStaffCache.remove(tenantId + ":" + userId);
        }
    }

    // non-deleted staff mappings of the user, cached for transformer.project.staff.cache.ttl.minutes
    private List<ProjectStaff> getProjectStaff(String userId, String tenantId) {
        String key = tenantId + ":" + userId;
        long ttlMillis = transformerProperties.getProjectStaffCacheTtlMinutes() != null
                ? transformerProperties.getProjectStaffCacheTtlMinutes() * 60_000L : 0L;
        CachedProjectStaff cached = userIdVsProjectStaffCache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < ttlMillis) {
            return cached.projectStaff;
        }
        List<ProjectStaff> projectStaffList = searchProjectStaff(Collections.singletonList(userId), tenantId);
        if (CollectionUtils.isEmpty(projectStaffList)) {
            // search failed or user not mapped yet, not cached so that a mapping created later is picked up
            return Collections.emptyList();
        }
        List<ProjectStaff> activeProjectStaff = projectStaffList.stream()
                .filter(projectStaff -> projectStaff != null && StringUtils.isNotBlank(projectStaff.getProjectId())
                        && !Boolean.TRUE.equals(projectStaff.getIsDeleted()))
                .collect(Collectors.toList());
        if (!activeProjectStaff.isEmpty()) {
            userIdVsProjectStaffCache.put(key, new CachedProjectStaff(activeProjectStaff, System.currentTimeMillis()));
        }
        return activeProjectStaff;
    }

    private Project getProjectCached(String projectId, String tenantId) {
        Project project = projectIdVsProjectCache.get(projectId);
        if (project == null) {
            project = getProject(projectId, tenantId);
            if (project != null) {
                projectIdVsProjectCache.put(projectId, project);
            }
        }
        return project;
    }

    private static long projectStartDate(Project project) {
        return project != null && project.getStartDate() != null ? project.getStartDate() : 0L;
    }

    // null or non-positive dates are treated as open ended
    private static boolean isWithin(long time, Long startDate, Long endDate) {
        return (startDate == null || startDate <= 0 || startDate <= time)
                && (endDate == null || endDate <= 0 || time <= endDate);
    }

    private static class CachedProjectStaff {
        private final List<ProjectStaff> projectStaff;
        private final long fetchedAt;

        private CachedProjectStaff(List<ProjectStaff> projectStaff, long fetchedAt) {
            this.projectStaff = projectStaff;
            this.fetchedAt = fetchedAt;
        }
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
            return response != null && response.getProjectStaff() != null ? response.getProjectStaff() : Collections.emptyList();
        } catch (Exception e) {
            log.error("Error while fetching project staff list {}", ExceptionUtils.getStackTrace(e));
            errorProducer.sendToErrorTopic(request, null, e);
            return null;
        }
    }

}
