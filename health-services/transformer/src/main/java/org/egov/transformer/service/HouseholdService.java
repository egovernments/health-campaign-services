package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkResponse;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.common.models.household.HouseholdSearchRequest;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.models.downstream.HouseholdIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class HouseholdService {
    private final TransformerProperties transformerProperties;
    private final ServiceRequestClient serviceRequestClient;
    private final Producer producer;
    private final CommonUtils commonUtils;

    public HouseholdService(TransformerProperties transformerProperties, ServiceRequestClient serviceRequestClient, Producer producer, CommonUtils commonUtils) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.producer = producer;
        this.commonUtils = commonUtils;
    }

    public List<Household> searchHousehold(String clientRefId, String tenantId) {
        HouseholdSearchRequest request = HouseholdSearchRequest.builder()
                .requestInfo(RequestInfo.builder().
                        userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .household(HouseholdSearch.builder().
                        clientReferenceId(Collections.singletonList(clientRefId)).build())
                .build();
        HouseholdBulkResponse response;
        try {
            StringBuilder uri = new StringBuilder();
            uri.append(transformerProperties.getHouseholdHost())
                    .append(transformerProperties.getHouseholdSearchUrl())
                    .append("?limit=").append(transformerProperties.getSearchApiLimit())
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);
            response = serviceRequestClient.fetchResult(uri,
                    request,
                    HouseholdBulkResponse.class);
        } catch (Exception e) {
            log.error("Error while fetching household for clientRefId: {}. ExceptionDetails: {}", clientRefId, ExceptionUtils.getStackTrace(e));
            return Collections.emptyList();
        }
        return response.getHouseholds();
    }

    public void transform(List<Household> payloadList) {
        String topic = transformerProperties.getTransformerProducerBulkHouseholdIndexV1Topic();
        log.info("transforming for ids {}", payloadList.stream()
                .map(Household::getId).collect(Collectors.toList()));
        List<HouseholdIndexV1> transformedPayloadList = payloadList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        if (!transformedPayloadList.isEmpty()) {
            producer.push(topic, transformedPayloadList);
            log.info("transformation successful");
        }
    }

    public HouseholdIndexV1 transform(Household household) {
        return HouseholdIndexV1.builder()
                .household(household)
                .geoPoint(commonUtils.getGeoPoint(household.getAddress()))
                .build();
    }
}
