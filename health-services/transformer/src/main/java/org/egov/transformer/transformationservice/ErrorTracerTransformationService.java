package org.egov.transformer.transformationservice;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.Role;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.FailedEventsIndex;
import org.egov.transformer.models.downstream.ProjectInfo;
import org.egov.transformer.models.error.ErrorDetails;
import org.egov.transformer.models.user.RequestInfo;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.*;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import org.egov.transformer.models.error.Error;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class ErrorTracerTransformationService {
    private final Producer producer;
    private final TransformerProperties transformerProperties;
    private final FacilityService facilityService;
    private final CommonUtils commonUtils;
    private final ProjectService projectService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final BoundaryService boundaryService;
    private final ProductService productService;
    private final ProjectFactoryService projectFactoryService;
    private static final Set<String> ADDITIONAL_DETAILS_DOUBLE_FIELDS = new HashSet<>(Arrays.asList(LAT, LNG));

    private Set<String> skipMessages;
    private Set<String> skipCodes;
    private Set<String> skipUrlContains;

    public ErrorTracerTransformationService(Producer producer, FacilityService facilityService, TransformerProperties transformerProperties, CommonUtils commonUtils, ProjectService projectService, UserService userService, ObjectMapper objectMapper, ProductService productService, BoundaryService boundaryService, ProjectFactoryService projectFactoryService) {
        this.producer = producer;
        this.facilityService = facilityService;
        this.transformerProperties = transformerProperties;
        this.commonUtils = commonUtils;
        this.projectService = projectService;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.boundaryService = boundaryService;
        this.productService = productService;
        this.projectFactoryService = projectFactoryService;
    }

    @PostConstruct
    private void init() {
        skipMessages = parseCommaSeparated(transformerProperties.getSkipErrorMessages());
        skipCodes = parseCommaSeparated(transformerProperties.getSkipErrorCodes());
        skipUrlContains = parseCommaSeparated(transformerProperties.getSkipUrlContains());
        log.info("ErrorTracer skip config - messages: {}, codes: {}, urlContains: {}",
                skipMessages, skipCodes, skipUrlContains);
    }

    public void transform(List<ErrorDetails> errorDetailsList) {
        log.info("transforming {} ERROR records", errorDetailsList.size());

        String baseTopic = transformerProperties.getTransformerProducerErrorDetailsIndexV1Topic();
        String stateLevelTenantId = transformerProperties.getStateLevelTenantId();

        List<FailedEventsIndex> failedEventsIndexList = errorDetailsList.stream()
                .filter(errorDetails -> !shouldSkip(errorDetails))
                .filter(errorDetails -> !shouldSkipUrl(errorDetails))
                .filter(errorDetails -> isStateLevelTenant(extractTenantId(errorDetails), stateLevelTenantId))
                .map(this::transformSafe)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (failedEventsIndexList.isEmpty()) {
            log.info("No records to push after filtering and transformation");
            return;
        }
        producer.push(baseTopic, failedEventsIndexList);
        // group by tenantId and push to tenant-specific topics
//        Map<String, List<FailedEventsIndex>> tenantGrouped = failedEventsIndexList.stream()
//                .collect(Collectors.groupingBy(
//                        index -> index.getTenantId() != null && !index.getTenantId().isEmpty()
//                                ? index.getTenantId() : "default"
//                ));
//
//        tenantGrouped.forEach((tenantId, indexList) -> {
//            String topic = "default".equals(tenantId) ? baseTopic : tenantId + "-" + baseTopic;
//            log.info("Pushing {} error records to topic: {}", indexList.size(), topic);
//
//        });
    }

    private FailedEventsIndex transformSafe(ErrorDetails errorDetails) {
        try {
            return transform(errorDetails);
        } catch (Exception e) {
            log.error("Failed to transform error record with id: {}, skipping", errorDetails.getUuid(), e);
            return null;
        }
    }

    private Set<String> parseCommaSeparated(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptySet();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private boolean isStateLevelTenant(String tenantId, String stateLevelTenantId) {
        if (stateLevelTenantId == null || stateLevelTenantId.isEmpty()) {
            return false;
        }
        if (tenantId == null || tenantId.isEmpty()) {
            log.debug("Skipping record with empty tenantId, state.level.tenantid={}", stateLevelTenantId);
            return false;
        }
        return tenantId.equals(stateLevelTenantId);
    }

    /**
     * Lightweight extraction of tenantId from requestBody without full deserialization.
     * Used for early filtering before expensive transform.
     */
    private String extractTenantId(ErrorDetails errorDetails) {
        try {
            if (errorDetails.getApiDetails() == null || errorDetails.getApiDetails().getRequestBody() == null) {
                return null;
            }
            JsonNode rootNode = objectMapper.readTree(errorDetails.getApiDetails().getRequestBody());
            return rootNode.path("RequestInfo").path("userInfo").path("tenantId").asText(null);
        } catch (Exception e) {
            log.debug("Could not extract tenantId for error id: {}", errorDetails.getUuid());
            return null;
        }
    }

    private boolean shouldSkipUrl(ErrorDetails errorDetails) {
        if (skipUrlContains.isEmpty()) {
            return false;
        }
        String url = errorDetails.getApiDetails() != null ? errorDetails.getApiDetails().getUrl() : null;
        if (url == null || url.isEmpty()) {
            return false;
        }
        for (String keyword : skipUrlContains) {
            if (url.contains(keyword)) {
                log.info("Skipping transformation for error id: {} with url containing: {}",
                        errorDetails.getUuid(), keyword);
                return true;
            }
        }
        return false;
    }

    private boolean shouldSkip(ErrorDetails errorDetails) {
        if (errorDetails.getErrors() == null || errorDetails.getErrors().isEmpty()) {
            return false;
        }
        Error firstError = errorDetails.getErrors().get(0);
        if (firstError == null) {
            return false;
        }
        if (!skipMessages.isEmpty() && firstError.getErrorMessage() != null
                && skipMessages.contains(firstError.getErrorMessage())) {
            log.info("Skipping transformation for error id: {} with errorMessage: {}",
                    errorDetails.getUuid(), firstError.getErrorMessage());
            return true;
        }
        if (!skipCodes.isEmpty() && firstError.getErrorCode() != null
                && skipCodes.contains(firstError.getErrorCode())) {
            log.info("Skipping transformation for error id: {} with errorCode: {}",
                    errorDetails.getUuid(), firstError.getErrorCode());
            return true;
        }
        return false;
    }

    /**
     * Derives errorCategory from URL pattern.
     * e.g. /household/member/v1/bulk/_create -> HH MEMBER Create Error
     *      /project/task/v1/bulk/_update    -> PROJECT TASK Update Error
     *      /stock/v1/bulk/_create           -> STOCK Create Error
     */
    private String resolveErrorCategory(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }

        String[] parts = url.split("/v1");
        String base = parts.length > 0 ? parts[0] : "";

        // Convert path to uppercase words
        String category = base.replace("/", " ")
                .trim()
                .toUpperCase();

        // Extract action (_create, _update, etc.)
        String action = "";
        int actionIndex = url.lastIndexOf("/_");
        if (actionIndex != -1) {
            action = url.substring(actionIndex + 2);
        }

        if (!category.isEmpty() && !action.isEmpty()) {
            return category + " " + action + ERROR_STRING;
        } else if (!category.isEmpty()) {
            return category + ERROR_STRING;
        }

        return "";
    }

    public FailedEventsIndex transform(ErrorDetails errorDetails) {

        String userName = "";
        String userId = "";
        String nameOfUser = "";
        List<String> roles = new ArrayList<>();
        String errorMessage = "";
        String errorCode = "";
        String exceptionMessage = "";
        String url = "";
        String tenantId = "";

        if (errorDetails.getErrors() != null && !errorDetails.getErrors().isEmpty()) {
            Error firstError = errorDetails.getErrors().get(0);
            if (firstError != null) {
                errorMessage = firstError.getErrorMessage() != null ? firstError.getErrorMessage() : "";
                errorCode = firstError.getErrorCode() != null ? firstError.getErrorCode() : "";
                if (firstError.getException() != null) {
                    exceptionMessage = firstError.getException().getMessage() != null
                            ? firstError.getException().getMessage() : "";
                }
            }
        }

        // extract url safely
        if (errorDetails.getApiDetails() != null && errorDetails.getApiDetails().getUrl() != null) {
            url = errorDetails.getApiDetails().getUrl();
        }

        // extract user info from requestBody
        try {
            String requestBody = errorDetails.getApiDetails() != null
                    ? errorDetails.getApiDetails().getRequestBody() : null;
            if (requestBody != null) {
                JsonNode rootNode = objectMapper.readTree(requestBody);
                JsonNode requestInfoNode = rootNode.path("RequestInfo");
                if (!requestInfoNode.isMissingNode()) {
                    RequestInfo requestInfo = objectMapper.treeToValue(requestInfoNode, RequestInfo.class);
                    if (requestInfo.getUserInfo() != null) {
                        userName = requestInfo.getUserInfo().getUserName() != null
                                ? requestInfo.getUserInfo().getUserName() : "";
                        userId = requestInfo.getUserInfo().getUuid() != null
                                ? requestInfo.getUserInfo().getUuid() : "";
                        nameOfUser = requestInfo.getUserInfo().getName() != null
                                ? requestInfo.getUserInfo().getName() : "";
                        tenantId = requestInfo.getUserInfo().getTenantId() != null
                                ? requestInfo.getUserInfo().getTenantId() : "";

                        if (requestInfo.getUserInfo().getRoles() != null) {
                            roles = requestInfo.getUserInfo().getRoles().stream()
                                    .map(Role::getCode)
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse requestBody for error id: {}, proceeding with available data",
                    errorDetails.getUuid(), e);
        }
        BoundaryHierarchyResult boundaryHierarchyResult = new BoundaryHierarchyResult();
        ProjectInfo projectInfo = new ProjectInfo();

        if (!userId.isEmpty() && !tenantId.isEmpty()) {
            try {
                projectInfo = commonUtils.projectDetailsFromUserId(userId, tenantId);
            } catch (Exception e) {
                log.warn("Could not fetch project details for userId: {}, tenantId: {}, proceeding without",
                        userId, tenantId, e);
            }
            if (projectInfo != null && projectInfo.getProjectId() != null) {
                try {
                    boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(
                            projectInfo.getProjectId(), tenantId);
                } catch (Exception e) {
                    log.warn("Could not fetch boundary hierarchy for projectId: {}, tenantId: {}, proceeding without",
                            projectInfo.getProjectId(), tenantId, e);
                }
            }
        }

        if (projectInfo == null) {
            projectInfo = new ProjectInfo();
        }
        if (boundaryHierarchyResult == null) {
            boundaryHierarchyResult = new BoundaryHierarchyResult();
        }

        FailedEventsIndex failedEventsIndex = FailedEventsIndex.builder()
                .id(errorDetails.getUuid())
                .auditDetails(errorDetails.getAuditDetails())
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                .exceptionMessage(exceptionMessage)
                .userName(userName)
                .userId(userId)
                .nameOfUser(nameOfUser)
                .role(roles)
                .url(url)
                .errorCategory(resolveErrorCategory(url))
                .tenantId(tenantId)
                .boundaryHierarchy(boundaryHierarchyResult.getBoundaryHierarchy())
                .boundaryHierarchyCode(boundaryHierarchyResult.getBoundaryHierarchyCode())
                .build();

        failedEventsIndex.setProjectInfo(projectInfo.getProjectId(), projectInfo.getProjectType(),
                projectInfo.getProjectTypeId(), projectInfo.getProjectName());
        failedEventsIndex.setCampaignNumber(projectInfo.getCampaignNumber());
        failedEventsIndex.setCampaignId(projectInfo.getCampaignId());

        return failedEventsIndex;
    }
}
