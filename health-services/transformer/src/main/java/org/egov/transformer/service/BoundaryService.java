package org.egov.transformer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.transformer.upstream.Boundary;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.boundary.BoundaryTree;
import org.egov.transformer.boundary.TreeGenerator;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class BoundaryService {

    private final TransformerProperties transformerProperties;

    private final ServiceRequestClient serviceRequestClient;

    private final ObjectMapper objectMapper;

    private final TreeGenerator treeGenerator;

    private static BoundaryTree cachedBoundaryTree = null;

    public BoundaryService(TransformerProperties transformerProperties,
                           ServiceRequestClient serviceRequestClient,
                           ObjectMapper objectMapper, TreeGenerator treeGenerator) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.objectMapper = objectMapper;
        this.treeGenerator = treeGenerator;
    }

    public BoundaryTree getBoundary(String code, String hierarchyTypeCode, String tenantId) {
        return generateLocationTree(code, hierarchyTypeCode, tenantId);
    }

    public BoundaryTree generateLocationTree (String code, String hierarchyTypeCode, String tenantId) {
        if(cachedBoundaryTree == null) {
            List<Boundary> boundaryList = searchBoundary(code, hierarchyTypeCode, tenantId);
            if(boundaryList.isEmpty()) return null;
            log.info("no cached boundary tree, adding current tree into the cache");
            BoundaryTree boundaryTree = generateTree(boundaryList.get(0));
            cachedBoundaryTree = boundaryTree;
            return search(cachedBoundaryTree, code);
        }
        else {
            log.info("fetching boundary {} from cached tree", code);
            BoundaryTree searchedFromCache = search(cachedBoundaryTree, code);
            if(searchedFromCache != null) {
                return searchedFromCache;
            } else {
                List<Boundary> boundaryList = searchBoundary(code, hierarchyTypeCode, tenantId);
                if(boundaryList.isEmpty()) return null;
                log.info("boundary code {} not in cached tree, generating new tree", code);
                BoundaryTree boundaryTree = generateTree(boundaryList.get(0));
                cachedBoundaryTree = boundaryTree;
                return search(cachedBoundaryTree, code);
            }
        }
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
                    RequestInfo.builder().build(),
                    LinkedHashMap.class);
        } catch (Exception e) {
            log.error("error while calling boundary service", e);
            throw new CustomException("BOUNDARY_ERROR", "error while calling boundary service");
        }
        if (response != null) {
            if (CollectionUtils.isEmpty(response)) {
                log.error("empty response received from boundary service");
                throw new CustomException("BOUNDARY_ERROR", "the response from location service is empty or null");
            }
            Configuration cf = Configuration.builder().options(Option.ALWAYS_RETURN_LIST).build();
            String jsonString = new JSONObject(response).toString();
            DocumentContext context = JsonPath.using(cf).parse(jsonString);
            JSONArray jsonArray = context.read("$.TenantBoundary[?(@.hierarchyType.code == 'ADMIN')].boundary");
            if (jsonArray != null) {
                String str = jsonArray.get(0).toString();
                try {
                    return Arrays.asList(objectMapper
                            .readValue(str,
                                    Boundary[].class));
                } catch (JsonProcessingException e) {
                    log.error("error in paring json", e);
                    throw new CustomException("JSON_ERROR", "error in parsing json");
                }
            }
            log.warn("boundary list is empty");
        }
        return Collections.emptyList();
    }
}
