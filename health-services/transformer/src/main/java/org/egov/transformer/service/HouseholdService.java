package org.egov.transformer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.household.*;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.producer.TransformerErrorProducer;
import org.springframework.stereotype.Component;

import java.util.*;

import static org.egov.transformer.Constants.*;

@Component
@Slf4j
public class HouseholdService {
    private final TransformerProperties transformerProperties;
    private final ServiceRequestClient serviceRequestClient;
    private final TransformerErrorProducer errorProducer;
    private static final Set<String> ADDITIONAL_DETAILS_INTEGER_FIELDS = new HashSet<>(Arrays.asList(PREGNANTWOMEN, CHILDREN, NO_OF_ROOMS, MEN_COUNT, WOMEN_COUNT));

    public HouseholdService(TransformerProperties transformerProperties, ServiceRequestClient serviceRequestClient, TransformerErrorProducer errorProducer) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.errorProducer = errorProducer;
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
            errorProducer.sendToErrorTopic(request, null, e);
            return Collections.emptyList();
        }
        return response != null && response.getHouseholds() != null ? response.getHouseholds() : Collections.emptyList();
    }

    public List<HouseholdMember> searchHouseholdMembers(HouseholdMemberSearch householdMemberSearch, String tenantId) {
        if (householdMemberSearch == null || StringUtils.isBlank(tenantId)) {
            log.warn("Skipping household member search, search criteria or tenantId missing. tenantId: {}", tenantId);
            return Collections.emptyList();
        }
        HouseholdMemberSearchRequest request = HouseholdMemberSearchRequest.builder()
                .requestInfo(RequestInfo.builder().
                        userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .householdMemberSearch(householdMemberSearch)
                .build();
        HouseholdMemberBulkResponse response;
        try {
            StringBuilder uri = new StringBuilder();
            // household member search is an endpoint of the household service, so it is derived from the household search url
            uri.append(transformerProperties.getHouseholdHost())
                    .append(transformerProperties.getHouseholdSearchUrl().replace(HOUSEHOLD_SEARCH_ENDPOINT, HOUSEHOLD_MEMBER_SEARCH_ENDPOINT))
                    .append("?limit=").append(transformerProperties.getSearchApiLimit())
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);
            response = serviceRequestClient.fetchResult(uri,
                    request,
                    HouseholdMemberBulkResponse.class);
        } catch (Exception e) {
            log.error("Error while fetching household members for search: {}. ExceptionDetails: {}", householdMemberSearch, ExceptionUtils.getStackTrace(e));
            errorProducer.sendToErrorTopic(request, null, e);
            return Collections.emptyList();
        }
        return response != null && response.getHouseholdMembers() != null ? response.getHouseholdMembers() : Collections.emptyList();
    }

    public void additionalFieldsToDetails(ObjectNode additionalDetails, Object additionalFields) {
        if (!(additionalFields instanceof List<?>)) {
            throw new IllegalArgumentException("additionalFields is not of the expected type List<Field>");
        }

        for (Object item : (List<?>) additionalFields) {
            if (item instanceof Field) {
                Field field = (Field) item;
                String key = field.getKey();
                String value = field.getValue();

                if (!additionalDetails.has(key)) {
                    if (ADDITIONAL_DETAILS_INTEGER_FIELDS.contains(key)) {
                        additionalDetails.put(key, parseInteger(value, key));
                    } else {
                        additionalDetails.put(key, value);
                    }
                }
            }
        }
    }

    private int parseInteger(String value, String key) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid number format for key '{}': value '{}'. Storing as 0.", key, value);
            return 0;
        }
    }
}
