package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.individual.*;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

import static org.egov.transformer.Constants.*;

@Service
@Slf4j
public class IndividualService {

    private final TransformerProperties properties;
    private final ServiceRequestClient serviceRequestClient;
    private final CommonUtils commonUtils;
    private static final List<String> INDIVIDUAL_INTEGER_ADDITIONAL_FIELDS = new ArrayList<>(Arrays.asList(HEIGHT));

    public IndividualService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient, CommonUtils commonUtils) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.commonUtils = commonUtils;
    }

    private Individual getIndividualByClientReferenceId(String clientReferenceId, String tenantId) {
        IndividualSearchRequest individualSearchRequest = IndividualSearchRequest.builder()
                .individual(IndividualSearch.builder().clientReferenceId(Collections.singletonList(clientReferenceId)).build())
                .requestInfo(RequestInfo.builder()
                        .userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .build();
        return fetchIndividual(individualSearchRequest, tenantId);
    }

    public Individual getIndividualById(String id, String tenantId) {
        IndividualSearchRequest individualSearchRequest = IndividualSearchRequest.builder()
                .individual(IndividualSearch.builder().id(Collections.singletonList(id)).build())
                .requestInfo(RequestInfo.builder()
                        .userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .build();
        return fetchIndividual(individualSearchRequest, tenantId);
    }

    private Individual fetchIndividual(IndividualSearchRequest individualSearchRequest, String tenantId) {
        try {
            IndividualBulkResponse response = serviceRequestClient.fetchResult(
                    new StringBuilder(properties.getIndividualHost()
                            + properties.getIndividualSearchUrl()
                            + "?limit=1"
                            + "&offset=0&tenantId=" + tenantId),
                    individualSearchRequest,
                    IndividualBulkResponse.class);

            // Check if response or individual list is null or empty
            return (response != null && !CollectionUtils.isEmpty(response.getIndividual())) ? response.getIndividual().get(0) : null;
        } catch (Exception e) {
            log.error("Error while fetching Individual Details for id {}, clRefId {}, Exception: {}",
                    individualSearchRequest.getIndividual().getId(), individualSearchRequest.getIndividual().getClientReferenceId(), ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    public Map<String, Object> getIndividualInfo(String clientReferenceId, String tenantId) {

        long indStartTime = System.currentTimeMillis();

        Individual individual = getIndividualByClientReferenceId(clientReferenceId, tenantId);

        long indEndTime = System.currentTimeMillis();
        long duration = indEndTime - indStartTime;

        log.info("Time taken to FETCH INDIVIDUAL for clientReferenceId {}: {} ms", clientReferenceId, duration);
        Map<String, Object> individualDetails = new HashMap<>();

        if (individual != null) {
            individualDetails.put(AGE, individual.getDateOfBirth() != null ? commonUtils.calculateAgeInMonthsFromDOB(individual.getDateOfBirth()) : null);
            individualDetails.put(GENDER, individual.getGender() != null ? individual.getGender().toString() : null);
            individualDetails.put(INDIVIDUAL_ID, clientReferenceId);
            individualDetails.put(DATE_OF_BIRTH, individual.getDateOfBirth() != null ? individual.getDateOfBirth().getTime() : null);

            if (individual.getAddress() != null && !individual.getAddress().isEmpty()
                    && individual.getAddress().get(0).getLocality() != null
                    && individual.getAddress().get(0).getLocality().getCode() != null) {
                individualDetails.put(ADDRESS_CODE, individual.getAddress().get(0).getLocality().getCode());
            }

            // Adding individualDetails if they are not null
            if (individual.getAdditionalFields() != null) {
                addIndividualAdditionalDetails(individual.getAdditionalFields(), individualDetails);
            }
        } else {
            individualDetails.put(AGE, null);
            individualDetails.put(GENDER, null);
            individualDetails.put(DATE_OF_BIRTH, null);
            individualDetails.put(INDIVIDUAL_ID, null);
        }

        return individualDetails;
    }

    private void addIndividualAdditionalDetails(AdditionalFields additionalFields, Map<String, Object> individualDetails) {
        if (additionalFields != null && additionalFields.getFields() != null) {
            additionalFields.getFields().forEach(field -> {
                String key = field.getKey();
                String value = field.getValue();
                if (key != null && value != null) {
                    if (INDIVIDUAL_INTEGER_ADDITIONAL_FIELDS.contains(key)) {
                        try {
                            individualDetails.put(key, Integer.valueOf(value));
                        } catch (NumberFormatException e) {
                            log.warn("Invalid number format for key '{}': value '{}'. Storing as null.", key, value);
                            individualDetails.put(key, null);
                        }
                    } else {
                        individualDetails.put(key, value);
                    }
                }
            });
        }
    }
}
