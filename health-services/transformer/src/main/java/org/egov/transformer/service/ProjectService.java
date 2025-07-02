package org.egov.transformer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import digit.models.coremodels.RequestInfoWrapper;
import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.project.*;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.springframework.stereotype.Component;
import org.egov.transformer.models.boundary.*;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
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

    private static Map<String, String> projectTypeIdVsProjectBeneficiaryCache = new HashMap<>();
    private static List<JsonNode> cachedProjectTypes = new ArrayList<>();
    private static Map<String, String> projectIdVsProjectTypeInfoCache = new ConcurrentHashMap<>();


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

    public String getProjectTypeInfoByProjectId(String projectId, String tenantId) {
        if (projectIdVsProjectTypeInfoCache.containsKey(projectId)) {
            log.info("Fetched ProjectTypeId and Type from cache for project id: {}:", projectId);
            return projectIdVsProjectTypeInfoCache.get(projectId);
        }
        Project project = getProject(projectId, tenantId);
        if (project != null) {
            String projectTypeIdAndType = project.getProjectTypeId() + ":" + project.getProjectType();
            projectIdVsProjectTypeInfoCache.put(projectId, projectTypeIdAndType);
            return projectTypeIdAndType;
        }
        return null;
    }

    public Map<String, String> getBoundaryCodeToNameMap(String locationCode, String tenantId) {
        List<EnrichedBoundary> boundaries = new ArrayList<>();
        RequestInfo requestInfo = RequestInfo.builder()
                .authToken(transformerProperties.getBoundaryV2AuthToken())
                .build();
        BoundaryRelationshipRequest  boundaryRequest = BoundaryRelationshipRequest.builder()
                .requestInfo(requestInfo).build();
        StringBuilder uri = new StringBuilder(transformerProperties.getBoundaryServiceHost()
                + transformerProperties.getBoundaryRelationshipSearchUrl()
                + "?includeParents=true&includeChildren=false&tenantId=" + tenantId
                + "&hierarchyType=" + transformerProperties.getBoundaryHierarchyName()
//                + "&boundaryType=" + transformerProperties.getBoundaryType()
                + "&codes=" + locationCode);
        log.info("URI: {}, \n, requestBody: {}", uri, requestInfo);
        try {
            // Fetch boundary details from the service
            log.debug("Fetching boundary relation details for tenantId: {}, boundary: {}", tenantId, locationCode);
            BoundarySearchResponse boundarySearchResponse = serviceRequestClient.fetchResult(
                    uri,
                    boundaryRequest,
                    BoundarySearchResponse.class
            );
            log.debug("Boundary Relationship details fetched successfully for tenantId: {}", tenantId);

            List<EnrichedBoundary> enrichedBoundaries = boundarySearchResponse.getTenantBoundary().stream()
                    .filter(hierarchyRelation -> !CollectionUtils.isEmpty(hierarchyRelation.getBoundary()))
                    .flatMap(hierarchyRelation -> hierarchyRelation.getBoundary().stream())
                    .collect(Collectors.toList());

            getAllBoundaryCodes(enrichedBoundaries, boundaries);

        } catch (Exception e) {
            log.error("Exception while searching boundaries for tenantId: {}, {}", tenantId, ExceptionUtils.getStackTrace(e));
            // Throw a custom exception if an error occurs during boundary search
            throw new CustomException("BOUNDARY_SEARCH_ERROR", e.getMessage());
        }

        Map<String, String> boundaryMap = new HashMap<>();

        boundaryMap =  boundaries.stream()
                .collect(Collectors.toMap(
                        EnrichedBoundary::getBoundaryType,
                        boundary -> {
                            String boundaryName = getBoundaryNameFromLocalisationService(boundary.getCode(), requestInfo, tenantId);
                            if(boundaryName == null) {
                                boundaryName = boundary.getCode().substring(boundary.getCode().lastIndexOf('_') + 1);
                            }
                            return boundaryName;
                        }
                ));
        return boundaryMap;
    }

    private String getBoundaryNameFromLocalisationService(String boundaryCode, RequestInfo requestInfo, String tenantId) {
        StringBuilder uri = new StringBuilder();
        RequestInfoWrapper requestInfoWrapper = new RequestInfoWrapper();
        requestInfoWrapper.setRequestInfo(requestInfo);
        uri.append(transformerProperties.getLocalizationHost()).append(transformerProperties.getLocalizationContextPath())
                .append(transformerProperties.getLocalizationSearchEndpoint())
                .append("?tenantId=" + tenantId)
                .append("&module=" + transformerProperties.getLocalizationModuleName())
                .append("&locale=" + transformerProperties.getLocalizationLocaleCode())
                .append("&codes=" + boundaryCode);
        List<String> codes = null;
        List<String> messages = null;
        Object result = null;
        try {
            result = serviceRequestClient.fetchResult(uri, requestInfoWrapper, Map.class);
            codes = JsonPath.read(result, LOCALIZATION_CODES_JSONPATH);
            messages = JsonPath.read(result, Constants.LOCALIZATION_MSGS_JSONPATH);
        } catch (Exception e) {
            log.error("Exception while fetching from localization: {}", ExceptionUtils.getStackTrace(e));
        }
        return CollectionUtils.isEmpty(messages) ? null : messages.get(0);
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
            log.error("error while fetching project list for ID {}, Exception: {}", projectId, ExceptionUtils.getStackTrace(e));
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
            return Collections.emptyList();
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
            JsonNode response = fetchMdmsResponse(requestInfo, tenantId, PROJECT_TYPES,
                    transformerProperties.getMdmsModule(), filter);

            if (response != null && response.has(transformerProperties.getMdmsModule())) {
                JsonNode projectBeneficiaryTypeNode = response
                        .get(transformerProperties.getMdmsModule())
                        .withArray(PROJECT_TYPES);

                if (projectBeneficiaryTypeNode != null && projectBeneficiaryTypeNode.isArray() && projectBeneficiaryTypeNode.size() > 0) {
                    String projectBeneficiaryType = projectBeneficiaryTypeNode.get(0).asText();
                    projectTypeIdVsProjectBeneficiaryCache.put(projectTypeId, projectBeneficiaryType);
                    return projectBeneficiaryType;
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
            JsonNode response = fetchMdmsResponse(requestInfo, tenantId, PROJECT_TYPES,
                    transformerProperties.getMdmsModule(), filter);
            projectTypes = convertToProjectTypeJsonNodeList(response);
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
            JsonNode response = fetchMdmsResponse(requestInfo, tenantId, PROJECT_TYPES,
                    transformerProperties.getMdmsModule(), filter);
            projectTypes = convertToProjectTypeJsonNodeList(response);
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

        JsonNode requiredProjectType = cachedProjectTypes.stream()
                .filter(projectType -> projectType.get(Constants.ID).asText().equals(projectTypeId))
                .findFirst()
                .orElse(null);

        if (requiredProjectType != null) {
            log.info("Fetched projectType from cache {}", projectTypeId);
            return requiredProjectType;
        }
        RequestInfo requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid("transformer-uuid").build())
                .build();
        try {
            JsonNode response = fetchMdmsResponse(requestInfo, tenantId, PROJECT_TYPES, transformerProperties.getMdmsModule(), filter);
            List<JsonNode> projectTypes = convertToProjectTypeJsonNodeList(response);
            cachedProjectTypes.addAll(projectTypes);
            return projectTypes.stream()
                    .filter(projectType -> projectType.get(Constants.ID).asText().equals(projectTypeId))
                    .findFirst()
                    .orElseGet(() -> objectMapper.createObjectNode());
//            JsonNode requiredProjectType = projectTypes.stream().filter(projectType -> projectType.get(Constants.ID).asText().equals(projectTypeId)).findFirst().get();
//            return requiredProjectType;
        } catch (IOException e) {
            return objectMapper.createObjectNode();
//            throw new RuntimeException(e);
        }
    }

    public JsonNode fetchProjectAdditionalDetails(String tenantId, String filter, String projectTypeId) {

        JsonNode additionalDetails = null;
        JsonNode requiredProjectType = fetchProjectTypes(tenantId, filter, projectTypeId);
        if (requiredProjectType.has(CYCLES) && !requiredProjectType.get(CYCLES).isEmpty()) {
            additionalDetails = extractProjectCycleAndDoseIndexes(requiredProjectType);
        }
        return additionalDetails;
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
            log.info("Error while fetching cycle and dose indexes from MDMS: {}", ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    private List<JsonNode> convertToProjectTypeJsonNodeList(JsonNode jsonNode) throws IOException {
        JsonNode projectTypesNode = jsonNode.get(transformerProperties.getMdmsModule()).withArray(PROJECT_TYPES);
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

    public List<ProjectStaff> searchProjectStaff(List<String> userId, String tenantId) {
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
            return null;
        }
    }

    public Map<String, String> getBoundaryHierarchyWithLocalityCode(String localityCode, String tenantId) {
        if (localityCode == null) {
            return null;
        }
        Map<String, String> boundaryLabelToNameMap = getBoundaryCodeToNameMap(localityCode, tenantId);
        Map<String, String> boundaryHierarchy = new HashMap<>();

        boundaryLabelToNameMap.forEach((label, value) -> {
            boundaryHierarchy.put(mdmsService.getMDMSTransformerElasticIndexLabels(label, tenantId), value);
        });
        return boundaryHierarchy;
    }

    public Map<String, String> getBoundaryHierarchyWithProjectId(String projectId, String tenantId) {
        Map<String, String> boundaryLabelToNameMap = getBoundaryCodeToNameMapByProjectId(projectId, tenantId);
        Map<String, String> boundaryHierarchy = new HashMap<>();

        boundaryLabelToNameMap.forEach((label, value) -> {
            boundaryHierarchy.put(mdmsService.getMDMSTransformerElasticIndexLabels(label, tenantId), value);
        });
        return boundaryHierarchy;
    }


}
