package org.egov.transformer.service;

        import com.fasterxml.jackson.databind.ObjectMapper;
        import org.egov.common.contract.request.RequestInfo;
        import org.egov.common.contract.request.User;
        import org.egov.common.models.individual.IndividualResponse;
        import org.egov.common.models.individual.IndividualSearch;
        import org.egov.common.models.individual.IndividualSearchRequest;
        import org.egov.transformer.config.TransformerProperties;
        import org.egov.transformer.http.client.ServiceRequestClient;
        import org.apache.commons.lang3.exception.ExceptionUtils;
        import lombok.extern.slf4j.Slf4j;
        import org.springframework.stereotype.Service;

        import java.util.*;
@Service
@Slf4j
public class IndividualService {

    private final TransformerProperties properties;

    private final ServiceRequestClient serviceRequestClient;


    private final ObjectMapper objectMapper;

    public IndividualService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient, ObjectMapper objectMapper) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.objectMapper = objectMapper;
    }



    public Date findIndividualByClientReferenceId(String clientReferenceId, String tenantId) {
        clientReferenceId="def23000-6d96-11ee-8bbb-4b7817e6c9cc";
        IndividualSearchRequest individualSearchRequest = IndividualSearchRequest.builder()
                .individual(IndividualSearch.builder().clientReferenceId(Collections.singletonList(clientReferenceId)).build())
                .requestInfo(RequestInfo.builder().
                        userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .build();
        IndividualResponse response;

        try {
            response = serviceRequestClient.fetchResult(
                    new StringBuilder(properties.getIndividualHost()
                            + properties.getIndividualSearchUrl()
                            + "?limit=1"
                            + "&offset=0&tenantId=" + tenantId),
                    individualSearchRequest,
                    IndividualResponse.class);
            Date dob= response.getIndividual().getDateOfBirth();
                return dob==null ? null : dob;
        } catch (Exception e) {
            log.error("error while fetching product {}", ExceptionUtils.getStackTrace(e));
            return null;
        }

    }
}
