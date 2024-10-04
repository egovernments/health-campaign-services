package org.egov.transformer.service;

import com.jayway.jsonpath.JsonPath;
import digit.models.coremodels.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.models.boundary.*;
import org.springframework.stereotype.Component;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.util.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.tracer.model.CustomException;


import java.util.stream.Collectors;
import java.util.*;

import static org.egov.transformer.Constants.LOCALIZATION_CODES_JSONPATH;

@Component
@Slf4j
public class BoundaryService {

    private final TransformerProperties transformerProperties;
    private final ServiceRequestClient serviceRequestClient;
    private final MdmsService mdmsService;

    public BoundaryService(TransformerProperties transformerProperties, ServiceRequestClient serviceRequestClient, MdmsService mdmsService) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.mdmsService = mdmsService;
    }

    public BoundaryHierarchyResult getBoundaryHierarchyWithLocalityCode(String localityCode, String tenantId) {
        if (localityCode == null) {
            return null;
        }
        // Fetch both localized and non-localized boundary data
        BoundaryHierarchyResult boundaryResult = getBoundaryCodeToNameMap(localityCode, tenantId);

        return applyTransformerElasticIndexLabels(boundaryResult, tenantId);
    }


    public BoundaryHierarchyResult getBoundaryCodeToNameMap(String locationCode, String tenantId) {
        RequestInfo requestInfo = RequestInfo.builder()
                .authToken(transformerProperties.getBoundaryV2AuthToken())
                .build();

        // Fetch boundaries
        List<EnrichedBoundary> boundaries = fetchBoundaryData(locationCode, tenantId);

        // Create and return BoundaryHierarchyResult
        return createBoundaryHierarchyResult(boundaries, tenantId, requestInfo);
    }

    public BoundaryHierarchyResult createBoundaryHierarchyResult(List<EnrichedBoundary> boundaries, String tenantId, RequestInfo requestInfo) {
        BoundaryHierarchyResult boundaryHierarchyResult = new BoundaryHierarchyResult();
        Map<String, String> boundaryMapToLocalizedNameMap = getBoundaryCodeToLocalizedNameMap(boundaries, requestInfo, tenantId);

        Map<String, String> boundaryCodeToLocalizationCodeMap = boundaries.stream()
                .collect(Collectors.toMap(
                        EnrichedBoundary::getBoundaryType,
                        EnrichedBoundary::getCode
                ));
        boundaryHierarchyResult.setBoundaryHierarchy(boundaryMapToLocalizedNameMap);
        boundaryHierarchyResult.setBoundaryHierarchyCode(boundaryCodeToLocalizationCodeMap);
        return boundaryHierarchyResult;
    }

    public List<EnrichedBoundary> fetchBoundaryData(String locationCode, String tenantId) {
        List<EnrichedBoundary> boundaries = new ArrayList<>();
        RequestInfo requestInfo = RequestInfo.builder()
                .authToken(transformerProperties.getBoundaryV2AuthToken())
                .build();
        BoundaryRelationshipRequest boundaryRequest = BoundaryRelationshipRequest.builder()
                .requestInfo(requestInfo).build();
        StringBuilder uri = new StringBuilder(transformerProperties.getBoundaryServiceHost()
                + transformerProperties.getBoundaryRelationshipSearchUrl()
                + "?includeParents=true&includeChildren=false&tenantId=" + tenantId
                + "&hierarchyType=" + transformerProperties.getBoundaryHierarchyName()
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

        return boundaries;
    }

    private Map<String, String> getBoundaryCodeToLocalizedNameMap(List<EnrichedBoundary> boundaries, RequestInfo requestInfo, String tenantId) {
        Map<String, String> boundaryMap = new HashMap<>();
        for (EnrichedBoundary boundary : boundaries) {
            String boundaryName = getBoundaryNameFromLocalisationService(boundary.getCode(), requestInfo, tenantId);
            if (boundaryName == null) {
                boundaryName = boundary.getCode().substring(boundary.getCode().lastIndexOf('_') + 1);
            }
            boundaryMap.put(boundary.getBoundaryType(), boundaryName);
        }
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
