package org.egov.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.individual.*;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.egov.transformer.Constants.*;

@Service
@Slf4j
public class IndividualService {

    private final TransformerProperties properties;

    private final ServiceRequestClient serviceRequestClient;


    private final ObjectMapper objectMapper;
    private final CommonUtils commonUtils;

    public IndividualService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient, ObjectMapper objectMapper, CommonUtils commonUtils) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.objectMapper = objectMapper;
        this.commonUtils = commonUtils;
    }



    public Map findIndividualByClientReferenceId(String clientReferenceId, String tenantId) {
        clientReferenceId = "def23000-6d96-11ee-8bbb-4b7817e6c9cc";
        IndividualSearchRequest individualSearchRequest = IndividualSearchRequest.builder()
                .individual(IndividualSearch.builder().clientReferenceId(Collections.singletonList(clientReferenceId)).build())
                .requestInfo(RequestInfo.builder().
                        userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .build();
        IndividualBulkResponse response;

        Map<String, Object> individualDetails = new HashMap<>();
        individualDetails.put(AGE, null);
        individualDetails.put(GENDER, null);
        individualDetails.put(DATE_OF_BIRTH, null);
        individualDetails.put(INDIVIDUAL_ID, null);

        try {
            response = serviceRequestClient.fetchResult(
                    new StringBuilder(properties.getIndividualHost()
                            + properties.getIndividualSearchUrl()
                            + "?limit=1"
                            + "&offset=0&tenantId=" + tenantId),
                    individualSearchRequest,
                    IndividualBulkResponse.class);
            Individual individual = response.getIndividual().get(0);

            individualDetails.put(AGE, commonUtils.calculateAgeInMonthsFromDOB(individual.getDateOfBirth()));
            individualDetails.put(GENDER, individual.getGender().toString());
            individualDetails.put(INDIVIDUAL_ID, individual.getIndividualId());
            individualDetails.put(DATE_OF_BIRTH, individual.getDateOfBirth().getTime());
            return individualDetails;
        } catch (Exception e) {
            log.error("error while fetching Individual Details: {}", ExceptionUtils.getStackTrace(e));
            return individualDetails;
        }

    }
}
