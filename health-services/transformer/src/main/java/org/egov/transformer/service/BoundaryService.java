package org.egov.transformer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.boundary.BoundaryTree;
import org.egov.transformer.boundary.TreeGenerator;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.upstream.Boundary;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class BoundaryService {

    private final TransformerProperties transformerProperties;

    private final ServiceRequestClient serviceRequestClient;

    private final ObjectMapper objectMapper;

    private final TreeGenerator treeGenerator;

    private static final Map<String, List<Boundary>> boundaryListMap = new ConcurrentHashMap<>();

    public BoundaryService(TransformerProperties transformerProperties,
                           ServiceRequestClient serviceRequestClient,
                           ObjectMapper objectMapper, TreeGenerator treeGenerator) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.objectMapper = objectMapper;
        this.treeGenerator = treeGenerator;
    }

    public List<Boundary> getBoundary(String code, String hierarchyTypeCode, String tenantId) {
        if (boundaryListMap.containsKey(code)) {
            return boundaryListMap.get(code);
        }
        List<Boundary> boundaryList = searchBoundary(code, hierarchyTypeCode, tenantId);
        if (!boundaryList.isEmpty()) {
            boundaryListMap.put(code, boundaryList);
        } else {
            boundaryList = Collections.emptyList();
        }
        return boundaryList;
    }

    public BoundaryTree generateTree(Boundary boundary) {
        return treeGenerator.generateTree(boundary);
    }

    public BoundaryTree search(BoundaryTree boundaryTree, String code) {
        return treeGenerator.search(boundaryTree, code);
    }

    private List<Boundary> searchBoundary(String code, String hierarchyTypeCode, String tenantId) {

        LinkedHashMap response;
        try {
            StringBuilder uri = new StringBuilder();
            uri.append(transformerProperties.getLocationHost())
                    .append(transformerProperties.getLocationSearchUrl())
                    .append("?limit=").append(transformerProperties.getSearchApiLimit())
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);
            if (hierarchyTypeCode != null) {
                uri.append("&hierarchyTypeCode=").append(hierarchyTypeCode);
            }
            uri.append("&code=").append(code);
            response = serviceRequestClient.fetchResult(uri,
                    RequestInfoWrapper.builder().build(),
                    LinkedHashMap.class);
        } catch (Exception e) {
            log.error("error while calling boundary service", e);
            throw new CustomException("BOUNDARY_ERROR", "error while calling boundary service");
        }
        if (response != null) {
            if (CollectionUtils.isEmpty(response))
                throw new CustomException("BOUNDARY_ERROR", "the response from location service is empty or null");
            JsonNode jsonNode = objectMapper.convertValue(response, JsonNode.class);
            JsonNode boundaryListNode = jsonNode.path("$.TenantBoundary[?(@.hierarchyType.code == 'ADMIN')].boundary");
            if (boundaryListNode != null) {
                return objectMapper.convertValue(boundaryListNode, new TypeReference<List<Boundary>>() {});
            }
        }
        return Collections.emptyList();
    }
}
