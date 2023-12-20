package org.egov.transformer.service;

        import com.fasterxml.jackson.databind.JsonNode;
        import com.fasterxml.jackson.databind.ObjectMapper;
        import org.egov.common.contract.request.RequestInfo;
        import org.egov.common.contract.request.User;
        import org.egov.common.models.individual.Individual;
        import org.egov.common.models.individual.IndividualSearch;
        import org.egov.common.models.individual.IndividualSearchRequest;
        import org.egov.common.models.product.Product;
        import org.egov.common.models.product.ProductSearch;
        import org.egov.common.models.product.ProductSearchRequest;
        import org.egov.common.models.transformer.upstream.Boundary;
        import org.egov.transformer.boundary.BoundaryTree;
        import org.egov.transformer.config.TransformerProperties;
        import org.egov.transformer.http.client.ServiceRequestClient;
        import org.apache.commons.lang3.exception.ExceptionUtils;
        import lombok.extern.slf4j.Slf4j;
        import org.springframework.stereotype.Service;

        import java.util.*;
        import java.util.concurrent.ConcurrentHashMap;
@Service
@Slf4j
public class IndividualService {

    private final TransformerProperties properties;

    private final ServiceRequestClient serviceRequestClient;

//    private static final Map<String, String> individualMap = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public IndividualService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient, ObjectMapper objectMapper) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.objectMapper = objectMapper;
    }

//    public void updateIndividualsInCache(List<Individual> individuals) {
//        individuals.forEach(individual -> individualMap.put(individual.getId(), individual.getId()));
//    }

    public String findIndividualById(String individualId, String tenantId) {
//        if (individualMap.containsKey(individualId)) {
//            return individualMap.get(individualId);
//        } else {
            IndividualSearchRequest individualSearchRequest = IndividualSearchRequest.builder()
                    .individual(IndividualSearch.builder().id(Collections.singletonList(individualId)).build())
                    .requestInfo(RequestInfo.builder().
                            userInfo(User.builder()
                                    .uuid("transformer-uuid")
                                    .build())
                            .build())
                    .build();

            try {
                JsonNode response = serviceRequestClient.fetchResult(
                        new StringBuilder(properties.getIndividualHost()
                                + properties.getIndividualSearchUrl()
                                + "?limit=1"
                                + "&offset=0&tenantId=" + tenantId),
                        individualSearchRequest,
                        JsonNode.class);
                List<Individual> individuals = Arrays.asList(objectMapper.convertValue(response.get("Individuals"), Individual[].class));
//                updateIndividualsInCache(individuals);
                return individuals.isEmpty() ? null : individuals.get(0).getId();
            } catch (Exception e) {
                log.error("error while fetching product {}", ExceptionUtils.getStackTrace(e));
                return null;
            }
//        }
    }

    public Date findIndividualByClientReferenceId(String clientReferenceId, String tenantId) {
        IndividualSearchRequest individualSearchRequest = IndividualSearchRequest.builder()
                .individual(IndividualSearch.builder().clientReferenceId(Collections.singletonList(clientReferenceId)).build())
                .requestInfo(RequestInfo.builder().
                        userInfo(User.builder()
                                .uuid("transformer-uuid")
                                .build())
                        .build())
                .build();

        try {
            JsonNode response = serviceRequestClient.fetchResult(
                    new StringBuilder(properties.getIndividualHost()
                            + properties.getIndividualSearchUrl()
                            + "?limit=1"
                            + "&offset=0&tenantId=" + tenantId),
                    individualSearchRequest,
                    JsonNode.class);
            List<Individual> individuals = Arrays.asList(objectMapper.convertValue(response.get("Individuals"), Individual[].class));
            return individuals.isEmpty() ? null : individuals.get(0).getDateOfBirth();
        } catch (Exception e) {
            log.error("error while fetching product {}", ExceptionUtils.getStackTrace(e));
            return null;
        }
    }
}
