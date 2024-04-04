package org.egov.processor.service;


import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.egov.processor.util.ParsingUtil;
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

    public GeoJsonParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void parseFileData(PlanConfiguration planConfig) {
        // Define the path to the GeoJSON file
        File file = new File("Microplan/valid/Population/Population.geojson");

        // Check if the GeoJSON file exists
        if (file.exists()) log.info("File exists at - " + file.getAbsolutePath());
        else log.info("FILE NOT FOUND - " + file.getAbsolutePath());

        try (GeoJSONReader reader = new GeoJSONReader(file.toURI().toURL())) {
            // Read the GeoJSON file and get the SimpleFeatureCollection
            SimpleFeatureCollection simpleFeatureCollection = reader.getFeatures();

            // Check if the SimpleFeatureCollection is empty
            if (simpleFeatureCollection.isEmpty()) {
                log.info("No features found in the GeoJSON data.");
            } else {
                log.info("Number of features: " + simpleFeatureCollection.size());
                // Get the attribute names from the SimpleFeatureCollection
                List<String> attributeNames = parsingUtil.getAttributeNames(simpleFeatureCollection);
                // Get the resource mapping list from the plan configuration
                List<ResourceMapping> resourceMappingList = planConfig.getResourceMapping();

                // Validate the attribute mapping
                boolean isValid = parsingUtil.validateAttributeMapping(attributeNames, resourceMappingList);
                if (isValid) {
                    log.info("Attribute mapping is valid.");
                } else {
                    log.info("Attribute mapping is invalid.");
                }

                // Iterate over the features in the SimpleFeatureCollection
                try (SimpleFeatureIterator iterator = simpleFeatureCollection.features()) {
                    while (iterator.hasNext()) {
                        SimpleFeature feature = iterator.next();
                        parsingUtil.printFeatureAttributes(feature, attributeNames);
                        // TODO:Process attribute values in accordance with resource mapping
                    }
                } catch (Exception e) {
                    // Handle any exception that occurs while reading the GeoJSON data
                    log.error("An error occurred while reading the GeoJSON data: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            // Throw a CustomException if an IOException occurs
            throw new CustomException("GEOJSON_PROCESSING_ERROR", "Exception while processing GeoJSON data");
        }

    }

}