package digit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.web.models.PlanConfiguration;
import digit.web.models.PlanConfigurationSearchCriteria;
import digit.web.models.PlanConfigurationSearchRequest;
import digit.web.models.ResourceMapping;
import java.io.File;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.data.geojson.GeoJSONReader;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class GeoJsonService {

    private ObjectMapper objectMapper;

    private PlanConfigurationService planConfigurationService;

    public GeoJsonService(ObjectMapper objectMapper, PlanConfigurationService planConfigurationService) {
        this.objectMapper = objectMapper;
        this.planConfigurationService = planConfigurationService;
    }

    public void planConfigProcessor() {
        //TODO:implement plan configuration search instead of this.
        PlanConfigurationSearchCriteria planConfigurationSearchCriteria = PlanConfigurationSearchCriteria.builder()
                .tenantId("mz").id("533db2ad-cfa7-42ce-b9dc-c2877c7405ca").build();
        PlanConfigurationSearchRequest planConfigurationSearchRequest = PlanConfigurationSearchRequest.builder().planConfigurationSearchCriteria(planConfigurationSearchCriteria).requestInfo(new RequestInfo()).build();
        List<PlanConfiguration> planConfigurationls = planConfigurationService.search(planConfigurationSearchRequest);

        parseShapeFileUsingLibrary(planConfigurationls.get(0));
        parseGeoJsonUsingLibrary(planConfigurationls.get(0));

    }

    public void parseShapeFileUsingLibrary(PlanConfiguration planConfig) {
        // Define the path to the shapefile
        File file = new File("Microplan/valid/Population/Population/Population.shp");

        // Check if the shapefile exists
        if (file.exists())
            log.info("File exists at - " + file.getAbsolutePath());
        else
            log.info("FILE NOT FOUND - " + file.getAbsolutePath());

        try {
            // Create a DataStore for the shapefile
            ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
            Charset charset = StandardCharsets.UTF_8;

            // Create a ShapefileDataStore and set the charset
            ShapefileDataStore dataStore = (ShapefileDataStore) dataStoreFactory.createDataStore(file.toURI().toURL());
            dataStore.setCharset(charset);

            // Get the type name (assumed to be the first type name)
            String typeName = dataStore.getTypeNames()[0];
            SimpleFeatureCollection simpleFeatureCollection = dataStore.getFeatureSource(typeName).getFeatures();

            // Check if the SimpleFeatureCollection is empty
            if (simpleFeatureCollection.isEmpty()) {
                log.info("No features found in the shapefile.");
            } else {
                log.info("Number of features: " + simpleFeatureCollection.size());


                // Get the attribute names from the SimpleFeatureCollection
                List<String> attributeNames = getAttributeNames(simpleFeatureCollection);
                // Get the resource mapping list from the plan configuration
                List<ResourceMapping> resourceMappingList = planConfig.getResourceMapping();

                // Validate the attribute mapping
                boolean isValid = validateAttributeMapping(attributeNames, resourceMappingList);
                if (isValid) {
                    log.info("Attribute mapping is valid.");
                } else {
                    log.info("Attribute mapping is invalid.");
                }

                // Iterate over the features in the SimpleFeatureCollection
                try (SimpleFeatureIterator features = simpleFeatureCollection.features()) {
                    while (features.hasNext()) {
                        SimpleFeature feature = features.next();
                        printFeatureAttributes(feature, attributeNames);
                        // TODO:Process attribute values in accordance with resource mapping
                    }
                }
            }

        } catch (IOException e) {
            // Throw a runtime exception if an IOException occurs
            throw new CustomException("SHAPEFILE_PROCESSING_ERROR", "Exception while processing Shape File data");
        }
    }



    public void parseGeoJsonUsingLibrary(PlanConfiguration planConfig) {
        // Define the path to the GeoJSON file
        File file = new File("Microplan/valid/Population/Population.geojson");

        // Check if the GeoJSON file exists
        if (file.exists())
            log.info("File exists at - " + file.getAbsolutePath());
        else
            log.info("FILE NOT FOUND - " + file.getAbsolutePath());

        try (GeoJSONReader reader = new GeoJSONReader(file.toURI().toURL())) {
            // Read the GeoJSON file and get the SimpleFeatureCollection
            SimpleFeatureCollection simpleFeatureCollection = reader.getFeatures();

            // Check if the SimpleFeatureCollection is empty
            if (simpleFeatureCollection.isEmpty()) {
                log.info("No features found in the GeoJSON data.");
            } else {
                log.info("Number of features: " + simpleFeatureCollection.size());
                // Get the attribute names from the SimpleFeatureCollection
                List<String> attributeNames = getAttributeNames(simpleFeatureCollection);
                // Get the resource mapping list from the plan configuration
                List<ResourceMapping> resourceMappingList = planConfig.getResourceMapping();

                // Validate the attribute mapping
                boolean isValid = validateAttributeMapping(attributeNames, resourceMappingList);
                if (isValid) {
                    log.info("Attribute mapping is valid.");
                } else {
                    log.info("Attribute mapping is invalid.");
                }

                // Iterate over the features in the SimpleFeatureCollection
                try (SimpleFeatureIterator iterator = simpleFeatureCollection.features()) {
                    while (iterator.hasNext()) {
                        SimpleFeature feature = iterator.next();
                        printFeatureAttributes(feature, attributeNames);
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


    public List<String> getAttributeNames(SimpleFeatureCollection simpleFeatureCollection) {
        List<String> attributeNames = new ArrayList<>();
        SimpleFeatureType featureType = simpleFeatureCollection.getSchema();
        List<AttributeDescriptor> descriptors = featureType.getAttributeDescriptors();

        for (AttributeDescriptor descriptor : descriptors) {
            String attributeName = descriptor.getLocalName();
            if (!attributeName.equals("geometry")) {
                attributeNames.add(attributeName);
            }
        }

        return attributeNames;
    }

    public boolean validateAttributeMapping(List<String> attributeNames, List<ResourceMapping> resourceMappingList) {
        Set<String> mappedFromSet = resourceMappingList.stream()
                .map(ResourceMapping::getMappedFrom)
                .collect(Collectors.toSet());

        for (String attributeName : attributeNames) {
            if (!mappedFromSet.contains(attributeName)) {
                return false;
            }
        }

        return true;
    }

    private void printFeatureAttributes(SimpleFeature feature, List<String> attributeNames) {
        for (String attributeName : attributeNames) {
            if (attributeName.equals("geometry") || attributeName.equals("the_geom")) continue;
            log.info( attributeName + " - " + feature.getAttribute(attributeName));
        }
        log.info("------------------------------------------");
    }


}
