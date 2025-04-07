package org.egov.processor.service;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.math.BigDecimal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.egov.processor.util.CalculationUtil;
import org.egov.processor.util.FilestoreUtil;
import org.egov.processor.util.ParsingUtil;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.ResourceMapping;

import org.egov.processor.web.models.campaignManager.CampaignResponse;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class GeoJsonParser implements FileParser {

    private ObjectMapper objectMapper;

    private ParsingUtil parsingUtil;

    private FilestoreUtil filestoreUtil;

    private CalculationUtil calculationUtil;

    public GeoJsonParser(ObjectMapper objectMapper, ParsingUtil parsingUtil, FilestoreUtil filestoreUtil, CalculationUtil calculationUtil) {
        this.objectMapper = objectMapper;
        this.parsingUtil = parsingUtil;
        this.filestoreUtil = filestoreUtil;
        this.calculationUtil = calculationUtil;
    }

    /**
     * Parses the file data based on the provided plan configuration and file store ID.
     * Converts the byte array data to a GeoJSON string, then to a JSON node.
     * Calculates resources based on the operations defined in the plan configuration.
     * Writes the updated JSON node to a file and uploads it to the file store.
     *
     * @param planConfigurationRequest  The plan configuration containing mapping and operation details.
     * @param fileStoreId The file store ID of the GeoJSON file to be parsed.
     * @return The file store ID of the uploaded updated file, or null if an error occurred.
     */
    @Override
    public Object parseFileData(PlanConfigurationRequest planConfigurationRequest, String fileStoreId, CampaignResponse campaignResponse) {
    	PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
        String geoJSON = parsingUtil.convertByteArrayToString(planConfig, fileStoreId);

        JsonNode jsonNode = parsingUtil.parseJson(geoJSON, objectMapper);

        List<String> columnNamesList = parsingUtil.fetchAttributeNamesFromJson(jsonNode);
        parsingUtil.validateColumnNames(columnNamesList, planConfig, fileStoreId);

        Map<String, BigDecimal> resultMap = new HashMap<>();
        Map<String, String> mappedValues = planConfig.getResourceMapping().stream()
        		.filter(f-> f.getFilestoreId().equals(fileStoreId))
                .collect(Collectors.toMap(ResourceMapping::getMappedTo, ResourceMapping::getMappedFrom));
        Map<String, BigDecimal> assumptionValueMap = calculationUtil.convertAssumptionsToMap(planConfig.getAssumptions());

        calculationUtil.calculateResources(jsonNode, planConfigurationRequest, resultMap, mappedValues, assumptionValueMap);

        File outputFile = parsingUtil.writeToFile(jsonNode, objectMapper);

        return filestoreUtil.uploadFile(outputFile, planConfig.getTenantId());

    }

}