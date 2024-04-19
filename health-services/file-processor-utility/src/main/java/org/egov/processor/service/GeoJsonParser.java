package org.egov.processor.service;


import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
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
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.geojson.GeoJSONReader;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
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

    public Object parseFileData(PlanConfiguration planConfig, String fileStoreId, String attributeToFetch) {
        File file = parsingUtil.getFileFromByteArray(planConfig, fileStoreId);

        Map<String, String> mappedValues = planConfig.getResourceMapping().stream()
                .collect(Collectors.toMap(ResourceMapping::getMappedTo, ResourceMapping::getMappedFrom));

        Map<String, BigDecimal> assumptionValueMap = calculationUtil.convertAssumptionsToMap(planConfig.getAssumptions());

        if (file != null && file.exists()) {
            try (GeoJSONReader reader = new GeoJSONReader(file.toURI().toURL())) {

                SimpleFeatureCollection simpleFeatureCollection = reader.getFeatures();
                if (!simpleFeatureCollection.isEmpty()) {
                    if (!parsingUtil.validateAttributeMapping(parsingUtil.getAttributeNames(simpleFeatureCollection), planConfig.getResourceMapping(), fileStoreId)) {
                        log.error("Attribute mapping is invalid.");
                        throw new CustomException("INVALID_ATTRIBUTE_MAPPING", "Attribute mapping is invalid.");
                    }
                }
                SimpleFeatureIterator iterator = simpleFeatureCollection.features();
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();

                    Map<String, BigDecimal> resultMap = new HashMap<>();

                    for (Operation operation : planConfig.getOperations()) {
                        String input = operation.getInput();
                        BigDecimal inputValue = null;

                        if (resultMap.containsKey(input)) {
                            inputValue = resultMap.get(input);
                        } else {//TODO check if geojson has an attribute as input
                            // Check if the input attribute exists in the current feature
                            if (feature.getAttribute(input) != null) {
                                // Assume input is a BigInteger attribute in GeoJSON
                                inputValue = (BigDecimal) feature.getAttribute(input);
                            } else {
                                throw new CustomException("INPUT_VALUE_NOT_FOUND", "Input value not found: " + input);
                            }
                        }

                        Operation.OperatorEnum operator = operation.getOperator();
                        BigDecimal assumptionValue = assumptionValueMap.get(operation.getAssumptionValue());
                        String output = operation.getOutput();

                        // Perform calculation based on the operator using the calculateResult method
                        BigDecimal result = calculationUtil.calculateResult(inputValue, operator, assumptionValue);

                        // Store the result in the map with the key as the output
                        resultMap.put(output, result);


//                    calculationUtil.calculateResources(planConfig, inputAttributeValue);

                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return null;

    }
            @Override
    public BigDecimal fetchPopulationData(PlanConfiguration planConfiguration, String fileStoreId) {
        return (BigDecimal) parseFileData(planConfiguration, fileStoreId, "attributeToFetch");

    }



    private Object findValueForLocality(SimpleFeatureCollection simpleFeatureCollection, String columnNameForLocality, String locality, String attributeToFetch) {
        try (SimpleFeatureIterator iterator = simpleFeatureCollection.features()) {
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                Object value = feature.getAttribute(columnNameForLocality);
                if (value != null && value.equals(locality)) {
                    Object result = feature.getAttribute(attributeToFetch);
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("An error occurred while processing the features: " + e.getMessage());
        }
        return null;
    }

}