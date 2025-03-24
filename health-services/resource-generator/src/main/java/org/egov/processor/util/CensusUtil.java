package org.egov.processor.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.Workflow;
import org.egov.processor.config.Configuration;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.kafka.Producer;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.census.*;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.egov.processor.config.ServiceConstants.*;

@Component
@Slf4j
public class CensusUtil {

    private ServiceRequestRepository serviceRequestRepository;

    private Configuration config;

    private Producer producer;

    private ParsingUtil parsingUtil;

    private ObjectMapper mapper;

    public CensusUtil(ServiceRequestRepository serviceRequestRepository, Configuration config, Producer producer, ParsingUtil parsingUtil, ObjectMapper objectMapper) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
        this.producer = producer;
        this.parsingUtil = parsingUtil;
        this.mapper = objectMapper;
    }

    /**
     * Creates and pushes a CensusRequest based on the provided plan configuration, feature JSON node, and mapped values.
     *
     * @param planConfigurationRequest The plan configuration request with the necessary details.
     * @param feature                  The JSON node containing feature data for the census.
     * @param mappedValues             A map of property names to their values from the feature node.
     * @param heirarchyType            The type of hierarchy to be used in the census.
     */
    public void create(PlanConfigurationRequest planConfigurationRequest, JsonNode feature, Map<String, String> mappedValues, String heirarchyType) {
        CensusRequest censusRequest = buildCensusRequest(planConfigurationRequest, feature, mappedValues, heirarchyType);
        try {
            producer.push(config.getResourceCensusCreateTopic(), censusRequest);
        } catch (Exception e) {
            log.error(ERROR_WHILE_PUSHING_TO_PLAN_SERVICE_FOR_LOCALITY + censusRequest.getCensus().getBoundaryCode(), e);
        }
    }

    /**
     * Builds and returns a CensusRequest using the provided plan configuration, feature JSON node, and mapped values.
     *
     * @param planConfigurationRequest The plan configuration request containing configuration details.
     * @param feature                  The feature JSON node containing property values.
     * @param mappedValues             The mapped values for extracting properties.
     * @param hierarchyType            The hierarchy type of the census.
     * @return A constructed CensusRequest object with populated details.
     */
    private CensusRequest buildCensusRequest(PlanConfigurationRequest planConfigurationRequest, JsonNode feature, Map<String, String> mappedValues, String hierarchyType) {

        PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
        return CensusRequest.builder()
                .census(Census.builder()
                        .tenantId(planConfig.getTenantId())
                        .hierarchyType(hierarchyType)
                        .boundaryCode((String) parsingUtil.extractMappedValueFromFeatureForAnInput(ServiceConstants.BOUNDARY_CODE, feature, mappedValues))
                        .type(Census.TypeEnum.PEOPLE)
                        .facilityAssigned(Boolean.FALSE)
                        .partnerAssignmentValidationEnabled(Boolean.TRUE)
                        .totalPopulation((BigDecimal) parsingUtil.extractMappedValueFromFeatureForAnInput(ServiceConstants.TOTAL_POPULATION, feature, mappedValues))
                        .workflow(Workflow.builder().action(WORKFLOW_ACTION_INITIATE).build())
                        .source(planConfig.getId())
                        .additionalFields(enrichAdditionalField(feature, mappedValues)).build())
                .requestInfo(planConfigurationRequest.getRequestInfo()).build();

    }

    /**
     * Enriches and returns additional details by extracting values from the feature JSON node based on the provided mappings.
     *
     * @param feature      The feature JSON node containing property values.
     * @param mappedValues The mapped values for extracting properties.
     * @return A map containing enriched additional details based on the extracted values.
     */
    public List<AdditionalField> enrichAdditionalField(JsonNode feature, Map<String, String> mappedValues) {
        // Initialize orderCounter inside the function
        List<AdditionalField> additionalFieldList =  new ArrayList<>();
        int orderCounter = 1;

        for (String key : mappedValues.keySet()) {
            // Skip keys in the override list
            if (config.getCensusAdditionalFieldOverrideKeys().contains(key))
                continue;

            // Get the corresponding value from the feature JsonNode
            Object valueFromRow = parsingUtil.extractMappedValueFromFeatureForAnInput(key, feature, mappedValues);

            // Check if the value exists in the JSON
            if (!ObjectUtils.isEmpty(valueFromRow)) {
                // Add additional fields with "UPLOADED" and "CONFIRMED" prefixes if key is in override list
                if (config.getCensusAdditionalPrefixAppendKeys().contains(key)) {
                    AdditionalField uploadedField = AdditionalField.builder()
                            .key(UPLOADED_KEY + key)
                            .value((BigDecimal) valueFromRow)
                            .editable(Boolean.FALSE)
                            .showOnUi(Boolean.TRUE)
                            .order(orderCounter++)  // Increment for "UPLOADED" field
                            .build();
                    additionalFieldList.add(uploadedField);

                    AdditionalField confirmedField = AdditionalField.builder()
                            .key(CONFIRMED_KEY + key)
                            .value((BigDecimal) valueFromRow)
                            .editable(Boolean.TRUE)
                            .showOnUi(Boolean.TRUE)
                            .order(orderCounter++)  // Increment for "CONFIRMED" field
                            .build();
                    additionalFieldList.add(confirmedField);
                } else {
                    AdditionalField additionalField = AdditionalField.builder()
                            .key(key)
                            .value((BigDecimal) valueFromRow)
                            .order(orderCounter++)  // Use and increment the local orderCounter
                            .build();
                    if(config.getCensusAdditionalFieldShowOnUIFalseKeys().contains(key)) {
                        additionalField.setShowOnUi(Boolean.FALSE);
                        additionalField.setEditable(Boolean.FALSE);
                    } else {
                        additionalField.setShowOnUi(Boolean.TRUE);
                        additionalField.setEditable(Boolean.TRUE);
                    }
                    additionalFieldList.add(additionalField);
                }
            }
        }

        return additionalFieldList;
    }

    /**
     * This method fetches data from Census based on the given census search request.
     *
     * @param searchRequest The census search request containing the search criteria.
     * @return returns the census response.
     */
    public CensusResponse fetchCensusRecords(CensusSearchRequest searchRequest) {

        // Get census search uri
        String uri = getCensusUri().toString();

        CensusResponse censusResponse = null;
        try {
           Object response = serviceRequestRepository.fetchResult(new StringBuilder(uri), searchRequest);
           censusResponse = mapper.convertValue(response, CensusResponse.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_CENSUS, e);
        }

        return censusResponse;
    }

    /**
     * Builds the census search uri.
     *
     * @return returns the complete uri for census search.
     */
    private StringBuilder getCensusUri() {
        return new StringBuilder().append(config.getCensusHost()).append(config.getCensusSearchEndPoint());
    }

}
