package org.egov.processor.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.egov.processor.web.models.Operation;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.ResourceMapping;
import org.egov.tracer.model.CustomException;

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

    public Object parseFileData(PlanConfiguration planConfig, String fileStoreId) {
        String geoJSON = parsingUtil.convertByteArrayToString(planConfig, fileStoreId);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = parsingUtil.parseJson(geoJSON, objectMapper);

        Map<String, BigDecimal> resultMap = new HashMap<>();
        Map<String, String> mappedValues = planConfig.getResourceMapping().stream()
                .collect(Collectors.toMap(ResourceMapping::getMappedTo, ResourceMapping::getMappedFrom));
        Map<String, BigDecimal> assumptionValueMap = calculationUtil.convertAssumptionsToMap(planConfig.getAssumptions());

        calculationUtil.calculateResources(jsonNode, planConfig.getOperations(), resultMap, mappedValues, assumptionValueMap);

        File outputFile = parsingUtil.writeToFile(jsonNode, objectMapper);

        return filestoreUtil.uploadFile(outputFile, planConfig.getTenantId());

    }

}