package org.egov.transformer.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.household.*;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class HouseholdService {
    private final TransformerProperties transformerProperties;
    private final ServiceRequestClient serviceRequestClient;

    public HouseholdService(TransformerProperties transformerProperties, ServiceRequestClient serviceRequestClient) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
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

    public void additionalFieldsToDetails(ObjectNode additionalDetails, Object additionalFields) {
        if (additionalFields instanceof List<?>) {
            List<?> fieldsList = (List<?>) additionalFields;

            for (Object item : fieldsList) {
                if (item instanceof Field) {
                    Field field = (Field) item;
                    // Check if the key already exists in additionalDetails
                    if (!additionalDetails.has(field.getKey())) {
                        additionalDetails.put(field.getKey(), field.getValue());
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("additionalFields is not of the expected type List<Field>");
        }
    }
}
