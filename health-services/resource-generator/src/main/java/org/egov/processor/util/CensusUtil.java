package org.egov.processor.util;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.Workflow;
import org.egov.processor.config.Configuration;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.kafka.Producer;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.processor.web.models.*;
import org.egov.processor.web.models.census.Census;
import org.egov.processor.web.models.census.CensusRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.egov.processor.config.ServiceConstants.*;

@Component
@Slf4j
public class CensusUtil {

    private ServiceRequestRepository serviceRequestRepository;

    private Configuration config;

    private Producer producer;

    private ParsingUtil parsingUtil;

    public CensusUtil(ServiceRequestRepository serviceRequestRepository, Configuration config, Producer producer, ParsingUtil parsingUtil) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
        this.producer = producer;
        this.parsingUtil = parsingUtil;
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
            log.info("Census request - " + censusRequest.getCensus());
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
     * @param heirarchyType            The hierarchy type of the census.
     * @return A constructed CensusRequest object with populated details.
     */
    private CensusRequest buildCensusRequest(PlanConfigurationRequest planConfigurationRequest, JsonNode feature, Map<String, String> mappedValues, String heirarchyType) {

        PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
        return CensusRequest.builder()
                .census(Census.builder()
                        .tenantId(planConfig.getTenantId())
                        .hierarchyType(heirarchyType)
                        .boundaryCode((String) parsingUtil.extractMappedValueFromFeatureForAnInput(ServiceConstants.BOUNDARY_CODE, feature, mappedValues))
                        .type(Census.TypeEnum.PEOPLE)
                        .facilityAssigned(Boolean.FALSE)
                        .partnerAssignmentValidationEnabled(Boolean.TRUE)
                        .totalPopulation((BigDecimal) parsingUtil.extractMappedValueFromFeatureForAnInput(ServiceConstants.TOTAL_POPULATION, feature, mappedValues))
                        .workflow(Workflow.builder().action(WORKFLOW_ACTION_INITIATE).comments(WORKFLOW_COMMENTS_INITIATING_CENSUS).build())
                        .source(planConfig.getId())
                        .additionalDetails(enrichAdditionalDetails(feature, mappedValues)).build())
                .requestInfo(planConfigurationRequest.getRequestInfo()).build();

    }

    /**
     * Enriches and returns additional details by extracting values from the feature JSON node based on the provided mappings.
     *
     * @param feature      The feature JSON node containing property values.
     * @param mappedValues The mapped values for extracting properties.
     * @return A map containing enriched additional details based on the extracted values.
     */
    public Map<String, Object> enrichAdditionalDetails(JsonNode feature, Map<String, String> mappedValues) {
        // Create additionalDetails object to hold the enriched data
        Map<String, Object> additionalDetails = new HashMap<>();

        // Iterate over mappedValues keys
        for (String key : mappedValues.keySet()) {
            // Get the corresponding value from the feature JsonNode
            Object valueFromRow = parsingUtil.extractMappedValueFromFeatureForAnInput(key, feature, mappedValues);
            // Check if the value exists in the JSON and add it to additonalDetails map
            if (!ObjectUtils.isEmpty(valueFromRow)) {
                additionalDetails.put(key, valueFromRow);
            }
        }

        return additionalDetails;
    }

}
