package org.egov.processor.service;


import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.util.ArrayList;
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
import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.FeatureWriter;
import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.data.SimpleFeatureWriter;
import org.geotools.api.data.Transaction;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.DataUtilities;
import org.geotools.data.geojson.GeoJSONReader;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
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
        DefaultFeatureCollection updatedCollection = new DefaultFeatureCollection();

        if (file != null && file.exists()) {
            try (GeoJSONReader reader = new GeoJSONReader(file.toURI().toURL())) {
//                // Assume existingFeatureType is your existing SimpleFeatureType
//                SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
//                builder.init(reader.getFeature().getType());
//
//                addOutputAttribute(builder, planConfig.getOperations());

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

                    // Define the new attribute (output) in the feature type
                    SimpleFeatureType featureType = feature.getFeatureType();
                    SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
                    builder.init(featureType);

                    addOutputAttribute(builder, planConfig.getOperations());

                    featureType = builder.buildFeatureType();

                    // Create a new feature with the updated feature type
                    SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);
                    featureBuilder.addAll(feature.getAttributes());

                    for (Operation operation : planConfig.getOperations()) {
                        String input = operation.getInput();
                        String inputFromMapping = mappedValues.get(input);
                        BigDecimal inputValue = null;

                        if (resultMap.containsKey(input)) {
                            inputValue = resultMap.get(input);
                        } else {//TODO check if geojson has an attribute as input
                            // Check if the input attribute exists in the current feature
                            if (feature.getAttribute(inputFromMapping) != null) {
                                // Assume input is a BigInteger attribute in GeoJSON
                                inputValue = new BigDecimal(String.valueOf(feature.getAttribute(inputFromMapping)));
                            } else {
                                throw new CustomException("INPUT_VALUE_NOT_FOUND", "Input value not found: " + input);
                            }
                        }

                        Operation.OperatorEnum operator = operation.getOperator();
                        BigDecimal assumptionValue = assumptionValueMap.get(operation.getAssumptionValue());

                        // Perform calculation based on the operator using the calculateResult method
                        BigDecimal result = calculationUtil.calculateResult(inputValue, operator, assumptionValue);

                        String output = operation.getOutput();
                        // Store the result in the map with the key as the output
                        resultMap.put(output, result);
                        //TODO: update geojson with the result in a new column output
                        // Set the output attribute for the feature
                        featureBuilder.set(output, result);
                    }
                    SimpleFeature newFeature = featureBuilder.buildFeature(feature.getID());
                    updatedCollection.add(newFeature);
                    System.out.println("new feature -> " + newFeature);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return writeFeatureCollectionToGeoJSON(updatedCollection, "/*/output.geojson");

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

    public void addOutputAttribute(SimpleFeatureTypeBuilder builder, List<Operation> operations) {
        // Iterate over the operations list
        for (Operation operation : operations) {
            String output = operation.getOutput();
            builder.add(output, BigDecimal.class); // Define the output attribute if not already present
        }
    }


 private File writeFeatureCollectionToGeoJSON(SimpleFeatureCollection featureCollection, String outputPath) {
//        org.geotools.data.DataStore newDataStore = null;
    /*    try {
            // Create a new GeoJSONDataStore to write the updated features
            File outputFile = new File(outputPath);
            Map<String, Serializable> params = new HashMap<>();
            params.put("url", outputFile.toURI().toURL());
            params.put("create spatial index", false);
            GeoJSONDataStore newDataStore = (GeoJSONDataStore) DataStoreFinder.getDataStore(params);
                if (newDataStore == null) {
                throw new RuntimeException("Unable to create data store.");
            }
            // Set the schema (feature type) for the new data store
            newDataStore.createSchema(featureCollection.getSchema());
            // Use the data store...
            System.out.println("Data store created successfully.");

            // Get a FeatureWriter to write features to the data store
            try (FeatureWriter<SimpleFeatureType, SimpleFeature> writer = newDataStore.getFeatureWriterAppend(newDataStore.getTypeNames()[0], Transaction.AUTO_COMMIT)) {
                SimpleFeatureIterator iterator = featureCollection.features();
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();
                    // Create a new feature with the updated attributes
                    SimpleFeature newFeature = writer.next();
                    newFeature.setAttributes(feature.getAttributes());
                    writer.write();
                }
                iterator.close();
            } catch (IOException e) {
                throw new RuntimeException("Error writing features to data store", e);
            }

            return outputFile; // Return the file object
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid output file URL", e);
        } catch (IOException e) {
            throw new RuntimeException("Error creating GeoJSON data store", e);
        } finally {
            if (newDataStore != null) {
                newDataStore.dispose();
            }
        }*/

     return new File(outputPath);
    }



}