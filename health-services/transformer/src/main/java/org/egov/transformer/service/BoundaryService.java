package org.egov.transformer.service;

import com.jayway.jsonpath.JsonPath;
import digit.models.coremodels.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.models.project.Project;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.models.boundary.*;
import org.egov.transformer.models.downstream.ProjectInfo;
import org.egov.transformer.producer.TransformerErrorProducer;
import org.springframework.stereotype.Component;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.util.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.tracer.model.CustomException;


import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.*;

import static org.egov.transformer.Constants.BOUNDARY_LOCALIZATION_PREWARM_FAILED;
import static org.egov.transformer.Constants.LOCALIZATION_MESSAGE;
import static org.egov.transformer.Constants.LOCALIZATION_MESSAGES_JSONPATH;
import static org.egov.transformer.Constants.LOCALIZATION_MESSAGE_CODE;

@Component
@Slf4j
public class BoundaryService {

    private final TransformerProperties transformerProperties;
    private final ServiceRequestClient serviceRequestClient;
    private final MdmsService mdmsService;
    private final ProjectService projectService;
    private final TransformerErrorProducer errorProducer;
    private static Map<String, String> boundaryCodeVsLocalizedName = new ConcurrentHashMap<>();
    private static Map<String, String> projectIdBoundaryCodeCache = new ConcurrentHashMap<>();

    private static Map<String, List<EnrichedBoundary>> cachedEnrichedBoundaries = new ConcurrentHashMap<>();

    private static final Set<String> prewarmedLocalizationModules = ConcurrentHashMap.newKeySet();

    public BoundaryService(TransformerProperties transformerProperties, ServiceRequestClient serviceRequestClient, MdmsService mdmsService, ProjectService projectService, TransformerErrorProducer errorProducer) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.mdmsService = mdmsService;
        this.projectService = projectService;
        this.errorProducer = errorProducer;
    }

    public BoundaryHierarchyResult getBoundaryHierarchyWithLocalityCode(String localityCode, String tenantId, String hierarchyType) {
        if (localityCode == null) {
            return new BoundaryHierarchyResult();
        }
        String resolvedHierarchyType = StringUtils.isBlank(hierarchyType)
                ? resolveHierarchyTypeForCode(localityCode, tenantId) : hierarchyType;
        if (resolvedHierarchyType == null) {
            return new BoundaryHierarchyResult();
        }
        // Fetch both localized and non-localized boundary data
        BoundaryHierarchyResult boundaryResult = getBoundaryCodeToNameMap(localityCode, tenantId, resolvedHierarchyType);
        return applyTransformerElasticIndexLabels(boundaryResult, tenantId);
    }

    /**
     * Finds which of the configured hierarchy types contains the given locality code, for records that
     * carry no hierarchy type of their own. Candidates are probed in configured order and the first hit
     * wins - the remaining ones are not looked at. Trees already in memory are checked first, so only a
     * hierarchy that has never been fetched costs a call to the boundary service.
     */
    private String resolveHierarchyTypeForCode(String locationCode, String tenantId) {
        List<String> candidateHierarchyTypes = transformerProperties.getBoundaryHierarchyTypes();
        if (CollectionUtils.isEmpty(candidateHierarchyTypes)) {
            log.warn("No boundary hierarchy types configured. Cannot resolve locality code: {}", locationCode);
            return null;
        }

        // First pass: trees already in memory, so a resolvable code never hits the boundary service.
        for (String candidate : candidateHierarchyTypes) {
            if (cachedHierarchyContainsCode(candidate, locationCode)) {
                log.info("Resolved hierarchyType {} for locality code {}", candidate, locationCode);
                return candidate;
            }
        }

        // Second pass: pull in any hierarchy not fetched yet, then re-check it. Once every candidate is
        // cached this loop is a no-op, so an unresolvable code never refetches.
        for (String candidate : candidateHierarchyTypes) {
            if (!CollectionUtils.isEmpty(cachedEnrichedBoundaries.get(candidate))) {
                continue;
            }
            try {
                loadBoundaryTree(tenantId, candidate);
            } catch (Exception e) {
                log.error("Could not load boundary tree for hierarchy type: {} while resolving locality code: {}, {}",
                        candidate, locationCode, ExceptionUtils.getStackTrace(e));
                continue;
            }
            if (cachedHierarchyContainsCode(candidate, locationCode)) {
                log.info("Resolved hierarchyType {} for locality code {} after loading its tree", candidate, locationCode);
                return candidate;
            }
        }

        log.warn("Locality code {} is not present in any configured hierarchy type: {}", locationCode, candidateHierarchyTypes);
        return null;
    }

    /** Whether an already cached hierarchy tree contains the code. Never calls the boundary service. */
    private boolean cachedHierarchyContainsCode(String hierarchyType, String locationCode) {
        List<EnrichedBoundary> cachedTree = cachedEnrichedBoundaries.get(hierarchyType);
        return !CollectionUtils.isEmpty(cachedTree)
                && !CollectionUtils.isEmpty(getEnrichedBoundaryPath(cachedTree, locationCode));
    }

    public BoundaryHierarchyResult getBoundaryCodeToNameMapByProjectId(String projectId, String tenantId) {
        if (projectIdBoundaryCodeCache.containsKey(projectId)) {
            ProjectInfo projectInfo = projectService.getProjectInfoByProjectId(projectId, tenantId);
            return getBoundaryCodeToNameMap(projectIdBoundaryCodeCache.get(projectId), tenantId, projectInfo.getHierarchyType());
        }
        Project project = projectService.getProject(projectId, tenantId);
        if (project == null) {
            return new BoundaryHierarchyResult();
        }
        String locationCode = project.getAddress().getBoundary();
        projectIdBoundaryCodeCache.put(projectId, locationCode);
        String hierarchyType = projectService.getHierarchyTypeFromProject(project);
        return getBoundaryCodeToNameMap(locationCode, tenantId, hierarchyType);
    }

    public BoundaryHierarchyResult getBoundaryHierarchyWithProjectId(String projectId, String tenantId) {
        BoundaryHierarchyResult boundaryLabelToNameMap = getBoundaryCodeToNameMapByProjectId(projectId, tenantId);
        return applyTransformerElasticIndexLabels(boundaryLabelToNameMap, tenantId);
    }


    public BoundaryHierarchyResult getBoundaryCodeToNameMap(String locationCode, String tenantId, String hierarchyType) {
        RequestInfo requestInfo = RequestInfo.builder()
                .authToken(transformerProperties.getBoundaryV2AuthToken())
                .build();

        // Fetch boundaries
        List<EnrichedBoundary> boundaries = fetchBoundaryData(locationCode, tenantId, hierarchyType);

        // Create and return BoundaryHierarchyResult
        return createBoundaryHierarchyResult(boundaries, tenantId, requestInfo, hierarchyType);
    }

    public BoundaryHierarchyResult createBoundaryHierarchyResult(List<EnrichedBoundary> boundaries, String tenantId, RequestInfo requestInfo, String hierarchyType) {
        BoundaryHierarchyResult boundaryHierarchyResult = new BoundaryHierarchyResult();
        Map<String, String> boundaryMapToLocalizedNameMap = getBoundaryCodeToLocalizedNameMap(boundaries, requestInfo, tenantId, hierarchyType);

        Map<String, String> boundaryCodeToLocalizationCodeMap = boundaries.stream()
                .collect(Collectors.toMap(
                        EnrichedBoundary::getBoundaryType,
                        EnrichedBoundary::getCode
                ));
        boundaryHierarchyResult.setBoundaryHierarchy(boundaryMapToLocalizedNameMap);
        boundaryHierarchyResult.setBoundaryHierarchyCode(boundaryCodeToLocalizationCodeMap);
        return boundaryHierarchyResult;
    }

    private List<EnrichedBoundary> getEnrichedBoundaryPath(List<EnrichedBoundary> enrichedBoundaries, String locationCode) {
        for (EnrichedBoundary enrichedBoundary : enrichedBoundaries) {
            List<EnrichedBoundary> path = getEnrichedBoundaryPath(enrichedBoundary, locationCode);
            if (path != null) {
                return path;
            }
        }
        return Collections.emptyList();
    }

    private List<EnrichedBoundary> getEnrichedBoundaryPath(EnrichedBoundary enrichedBoundary, String locationCode) {
        if (locationCode.equals(enrichedBoundary.getCode())) {
            EnrichedBoundary matchedNode = new EnrichedBoundary();
            matchedNode.setCode(enrichedBoundary.getCode());
            matchedNode.setBoundaryType(enrichedBoundary.getBoundaryType());
            matchedNode.setChildren(Collections.emptyList());
            return Collections.singletonList(matchedNode);
        }

        if (enrichedBoundary.getChildren() != null && !enrichedBoundary.getChildren().isEmpty()) {
            for (EnrichedBoundary child : enrichedBoundary.getChildren()) {
                List<EnrichedBoundary> childPath = getEnrichedBoundaryPath(child, locationCode);
                if (childPath != null) {
                    EnrichedBoundary currentNode = new EnrichedBoundary();
                    currentNode.setCode(enrichedBoundary.getCode());
                    currentNode.setBoundaryType(enrichedBoundary.getBoundaryType());
                    currentNode.setChildren(Collections.emptyList());

                    List<EnrichedBoundary> fullPath = new ArrayList<>();
                    fullPath.add(currentNode);
                    fullPath.addAll(childPath);
                    return fullPath;
                }
            }
        }

        return null;
    }


    public List<EnrichedBoundary> fetchBoundaryData(String locationCode, String tenantId, String hierarchyType) {
        // Guard the cache lookup below: ConcurrentHashMap rejects null keys, so a caller that could not
        // resolve a hierarchy type would otherwise fail with an NPE rather than fall back.
        String hierarchy = StringUtils.isBlank(hierarchyType)
                ? transformerProperties.getBoundaryHierarchyName() : hierarchyType;

        List<EnrichedBoundary> cachedBoundariesForHierarchy = cachedEnrichedBoundaries.get(hierarchy);
        if (!CollectionUtils.isEmpty(cachedBoundariesForHierarchy)) {
            log.debug("Fetching boundary info from cached boundary for code: {}", locationCode);
            List<EnrichedBoundary> finalEnrichedBoundary = getEnrichedBoundaryPath(cachedBoundariesForHierarchy, locationCode);
            if (!CollectionUtils.isEmpty(finalEnrichedBoundary)) {
                return finalEnrichedBoundary;
            }
        }

        log.info("Could not fetch boundary info from cached tree. Fetching from service for locationCode: {}", locationCode);
        return getEnrichedBoundaryPath(loadBoundaryTree(tenantId, hierarchy), locationCode);
    }

    /**
     * Fetches a hierarchy's full boundary tree, caches it, and prewarms its localizations. Kept separate
     * from {@link #fetchBoundaryData} so the hierarchy probe can load a tree without also triggering the
     * "code not found, refetch" path.
     */
    private List<EnrichedBoundary> loadBoundaryTree(String tenantId, String hierarchyType) {
        RequestInfo requestInfo = RequestInfo.builder()
                .authToken(transformerProperties.getBoundaryV2AuthToken())
                .userInfo(User.builder().uuid("transformer-uui").tenantId(tenantId).build())
                .build();
        BoundaryRelationshipRequest boundaryRequest = BoundaryRelationshipRequest.builder()
                .requestInfo(requestInfo).build();
        StringBuilder uri = new StringBuilder(transformerProperties.getBoundaryServiceHost()
                + transformerProperties.getBoundaryRelationshipSearchUrl()
                + "?includeParents=true&includeChildren=true&tenantId=" + tenantId
                + "&hierarchyType=" + hierarchyType
        );
        log.info("Fetching boundary relationships, URI: {}", uri);
        try {
            log.debug("Fetching boundary relation details for tenantId: {}, hierarchyType: {}", tenantId, hierarchyType);
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
            cachedEnrichedBoundaries.put(hierarchyType, enrichedBoundaries);
            log.info("Cached boundary object for hierarchy type: {}", hierarchyType);
            prewarmBoundaryLocalizations(enrichedBoundaries, requestInfo, tenantId, hierarchyType);
            return enrichedBoundaries;

        } catch (Exception e) {
            log.error("Exception while searching boundaries for tenantId: {}, {}", tenantId, ExceptionUtils.getStackTrace(e));
            // Do not emit an error record here: the exception propagates to the consumer,
            // which pushes a single error record with the correct source topic and the
            // original payload. Emitting here would produce a duplicate record with topic=null.
            throw new CustomException("BOUNDARY_SEARCH_ERROR", e.getMessage());
        }
    }

    private Map<String, String> getBoundaryCodeToLocalizedNameMap(
            List<EnrichedBoundary> boundaries, RequestInfo requestInfo, String tenantId, String hierarchyType) {

        Map<String, String> boundaryMap = new HashMap<>();

        for (EnrichedBoundary boundary : boundaries) {
            String boundaryCode = boundary.getCode();
            String boundaryName = getLocalizedBoundaryName(boundaryCode, requestInfo, tenantId, hierarchyType);

            boundaryMap.put(boundary.getBoundaryType(), boundaryName);
        }
        return boundaryMap;
    }

    private String getLocalizedBoundaryName(String boundaryCode, RequestInfo requestInfo, String tenantId, String hierarchyType) {
        String cachedName = boundaryCodeVsLocalizedName.get(boundaryCode);

        if (cachedName != null) {
            return cachedName;
        }

        String fetchedName = getBoundaryNameFromLocalisationService(boundaryCode, requestInfo, tenantId, hierarchyType);
        if (fetchedName == null) {
            fetchedName = boundaryCode.substring(boundaryCode.lastIndexOf('_') + 1);
        } else {
            boundaryCodeVsLocalizedName.put(boundaryCode, fetchedName);
            log.info("Fetched localization from service for code: {}, value: {}. Cached result.", boundaryCode, fetchedName);
        }

        return fetchedName;
    }

    private String getBoundaryNameFromLocalisationService(String boundaryCode, RequestInfo requestInfo, String tenantId, String hierarchyType) {
        StringBuilder uri = new StringBuilder();
        RequestInfoWrapper requestInfoWrapper = requestInfoWrapperOf(requestInfo);
        uri.append(getLocalizationSearchUri(tenantId, hierarchyType))
                .append("&codes=" + boundaryCode);
        List<String> messages = null;
        try {
            Object result = serviceRequestClient.fetchResult(uri, requestInfoWrapper, Map.class);
            messages = JsonPath.read(result, Constants.LOCALIZATION_MSGS_JSONPATH);
        } catch (Exception e) {
            log.error("Exception while fetching from localization: {}", ExceptionUtils.getStackTrace(e));
            errorProducer.sendToErrorTopic(requestInfoWrapper, null, e);
        }
        return CollectionUtils.isEmpty(messages) ? null : messages.get(0);
    }

    /**
     * Fetches every message in the hierarchy's localization module in a single call and caches the subset
     * whose code belongs to the fetched boundary tree. Runs at most once per tenant + module + locale:
     * whichever thread gets there first does the fetch, and every other thread carries on with the
     * per-code lookup instead of waiting on it. Never throws - {@link #getLocalizedBoundaryName} stays
     * the fallback for anything the module did not return.
     */
    private void prewarmBoundaryLocalizations(List<EnrichedBoundary> enrichedBoundaries, RequestInfo requestInfo,
                                              String tenantId, String hierarchyType) {
        String cacheKey = tenantId + "|" + getLocalizationModule(hierarchyType)
                + "|" + transformerProperties.getLocalizationLocaleCode();

        // Only the thread that claims the key prewarms; the rest return straight away.
        if (!prewarmedLocalizationModules.add(cacheKey)) {
            return;
        }

        try {
            List<EnrichedBoundary> allBoundaries = new ArrayList<>();
            getAllBoundaryCodes(enrichedBoundaries, allBoundaries);
            Set<String> boundaryCodes = allBoundaries.stream()
                    .map(EnrichedBoundary::getCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (boundaryCodes.isEmpty()) {
                prewarmedLocalizationModules.remove(cacheKey);
                log.info("No boundary codes found in hierarchy: {}. Skipping localization prewarm.", hierarchyType);
                return;
            }

            // Only the filtered map crosses back from this call, so the full module response is
            // unreachable - and collectable - from here on.
            Map<String, String> boundaryCodeVsName = fetchModuleLocalizations(boundaryCodes, requestInfoWrapperOf(requestInfo), tenantId, hierarchyType);
            boundaryCodeVsLocalizedName.putAll(boundaryCodeVsName);
            log.info("Prewarmed localizations for cacheKey: {}, boundary codes in hierarchy: {}, cached: {}",
                    cacheKey, boundaryCodes.size(), boundaryCodeVsName.size());
        } catch (Exception e) {
            // Let a later record retry the prewarm; until then the per-code lookup keeps working.
            prewarmedLocalizationModules.remove(cacheKey);
            log.error("Exception while prewarming localizations for cacheKey: {}. Falling back to per-code lookup, {}",
                    cacheKey, ExceptionUtils.getStackTrace(e));
            // The record itself still indexes via the per-code fallback, so this is not a failed record.
            // Emitted only to make the outage visible: the payload names the prewarm so its deterministic
            // id cannot collide with a genuinely failed record's doc from the same source topic.
            errorProducer.sendToErrorTopic(BOUNDARY_LOCALIZATION_PREWARM_FAILED + cacheKey, null, e);
        }
    }

    /**
     * Searches localization without a codes filter, which returns every message in the module for the
     * tenant and locale, and keeps only the messages that map to one of the given boundary codes.
     */
    private Map<String, String> fetchModuleLocalizations(Set<String> boundaryCodes, RequestInfoWrapper requestInfoWrapper,
                                                         String tenantId, String hierarchyType) throws Exception {
        StringBuilder uri = new StringBuilder(getLocalizationSearchUri(tenantId, hierarchyType));
        log.info("Prewarming boundary localizations, URI: {}", uri);

        Object result = serviceRequestClient.fetchResult(uri, requestInfoWrapper, Map.class);
        // Read the messages array itself rather than zipping $.messages.*.code against
        // $.messages.*.message: a wildcard skips entries missing a key, which would shift the two
        // lists out of step and cache a name against the wrong code.
        List<Map<String, Object>> messages = JsonPath.read(result, LOCALIZATION_MESSAGES_JSONPATH);

        Map<String, String> boundaryCodeVsName = new HashMap<>();
        if (CollectionUtils.isEmpty(messages)) {
            return boundaryCodeVsName;
        }
        for (Map<String, Object> message : messages) {
            Object code = message.get(LOCALIZATION_MESSAGE_CODE);
            Object localizedName = message.get(LOCALIZATION_MESSAGE);
            if (code != null && localizedName != null && boundaryCodes.contains(code.toString())) {
                boundaryCodeVsName.put(code.toString(), localizedName.toString());
            }
        }
        log.info("Localization module returned {} messages, {} matched boundary codes in hierarchy: {}",
                messages.size(), boundaryCodeVsName.size(), hierarchyType);
        return boundaryCodeVsName;
    }

    private String getLocalizationSearchUri(String tenantId, String hierarchyType) {
        return transformerProperties.getLocalizationHost() + transformerProperties.getLocalizationContextPath()
                + transformerProperties.getLocalizationSearchEndpoint()
                + "?tenantId=" + tenantId
                + "&module=" + getLocalizationModule(hierarchyType)
                + "&locale=" + transformerProperties.getLocalizationLocaleCode();
    }

    private String getLocalizationModule(String hierarchyType) {
        // Locale.ROOT keeps the module name stable regardless of the JVM default locale.
        String hierarchy = StringUtils.isBlank(hierarchyType)
                ? transformerProperties.getBoundaryHierarchyName() : hierarchyType;
        return transformerProperties.getLocalizationModuleName() + hierarchy.toLowerCase(Locale.ROOT);
    }

    private RequestInfoWrapper requestInfoWrapperOf(RequestInfo requestInfo) {
        RequestInfoWrapper requestInfoWrapper = new RequestInfoWrapper();
        requestInfoWrapper.setRequestInfo(requestInfo);
        return requestInfoWrapper;
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

    public BoundaryHierarchyResult applyTransformerElasticIndexLabels(BoundaryHierarchyResult boundaryResult, String tenantId) {
        Map<String, String> localizedBoundaryHierarchy = new HashMap<>();
        Map<String, String> nonLocalizedBoundaryHierarchyCode = new HashMap<>();

        boundaryResult.getBoundaryHierarchy().forEach((boundaryType, localizedName) -> {
            // Generate elastic index label
            String label = mdmsService.getMDMSTransformerElasticIndexLabels(boundaryType, tenantId);

            // Populate localized and non-localized maps
            localizedBoundaryHierarchy.put(label, localizedName);
            nonLocalizedBoundaryHierarchyCode.put(label, boundaryResult.getBoundaryHierarchyCode().get(boundaryType));
        });

        // Return the result as a BoundaryHierarchyResult
        return BoundaryHierarchyResult.builder()
                .boundaryHierarchy(localizedBoundaryHierarchy)
                .boundaryHierarchyCode(nonLocalizedBoundaryHierarchyCode)
                .build();
    }


}
