package org.egov.transformer.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.egov.transformer.config.TransformerProperties;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.transformer.models.upstream.ServiceDefinition;
import org.egov.transformer.models.upstream.ServiceDefinitionCriteria;
import org.egov.transformer.models.upstream.ServiceDefinitionResponse;
import org.egov.transformer.models.upstream.ServiceDefinitionSearchRequest;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ServiceDefinitionService {


    private final TransformerProperties transformerProperties;
    private final ServiceRequestClient serviceRequestClient;
    private static final Map<String, ServiceDefinition> serviceMap = new ConcurrentHashMap<>();


    public ServiceDefinitionService( TransformerProperties transformerProperties, ServiceRequestClient serviceRequestClient) {


        this.transformerProperties = transformerProperties;

        this.serviceRequestClient = serviceRequestClient;
    }

    public ServiceDefinition getServiceDefinition(String serviceDefId, String tenantId) {

        if (serviceMap.containsKey(serviceDefId)) {
            log.info("getting project {} from cache",serviceDefId);
            return serviceMap.get(serviceDefId);
        }
        List<ServiceDefinition> serviceDefinitionList = searchServiceDefinition(serviceDefId, tenantId);
        ServiceDefinition serviceDefinition=null;
        if (!serviceDefinitionList.isEmpty()) {
            serviceDefinition = serviceDefinitionList.get(0);
            serviceMap.put(serviceDefId, serviceDefinition);
        }
        return serviceDefinition;
    }

    private List<ServiceDefinition> searchServiceDefinition(String serviceDefId, String tenantId) {

        ServiceDefinitionSearchRequest request = ServiceDefinitionSearchRequest.builder()
                .serviceDefinitionCriteria(ServiceDefinitionCriteria.builder().ids(Collections.singletonList(serviceDefId)).tenantId(tenantId).build())
                .requestInfo(RequestInfo.builder()
                                .userInfo(User.builder()
                                        .uuid("transformer-uuid")
                                        .build())
                        .build())
                .build();


       ServiceDefinitionResponse response;
        try {
            StringBuilder uri = new StringBuilder();
            uri.append(transformerProperties.getServiceDefinitionHost())
                    .append(transformerProperties.getServiceDefinitionSearchUrl())
                    .append("?limit=").append(transformerProperties.getSearchApiLimit())
                    .append("&offset=0")
                    .append("&ids=").append(serviceDefId)
                    .append("&tenantId=").append(tenantId);
            response = serviceRequestClient.fetchResult(uri,
                    request,
                    ServiceDefinitionResponse.class);
        } catch (Exception e) {
            log.error("error while fetching serviceDefinition list", e);
            throw new CustomException("ServiceDefinition_FETCH_ERROR",
                    "error while fetching service details for id: " + serviceDefId);
        }
        return response.getServiceDefinition();
    }
}
