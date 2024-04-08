package org.egov.processor.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.egov.processor.util.FilestoreUtil;
import org.egov.processor.util.ParsingUtil;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.ResourceMapping;
import org.egov.tracer.model.CustomException;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ShapeFileParser implements FileParser {

    private ParsingUtil parsingUtil;

    private FilestoreUtil filestoreUtil;

    public ShapeFileParser(ParsingUtil parsingUtil) {
        this.parsingUtil = parsingUtil;
    }

    public void parseFileData(PlanConfiguration planConfig) {
        // Define the path to the shapefile
        byte[] byteArray = filestoreUtil.getFile(planConfig.getTenantId(), planConfig.getFiles().get(0).getFilestoreId());
        File file = parsingUtil.convertByteArrayToFile(byteArray, "shapefile");

        // Check if the GeoJSON file exists
        if (file == null || !file.exists()) log.info("FILE NOT FOUND - ");
        else log.info("File exists at - " + file.getAbsolutePath());

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
                try (SimpleFeatureIterator features = simpleFeatureCollection.features()) {
                    while (features.hasNext()) {
                        SimpleFeature feature = features.next();
                        parsingUtil.printFeatureAttributes(feature, attributeNames);
                        // TODO:Process attribute values in accordance with resource mapping
                    }
                }
            }

        } catch (IOException e) {
            // Throw a runtime exception if an IOException occurs
            throw new CustomException("SHAPEFILE_PROCESSING_ERROR", "Exception while processing Shape File data");
        }
    }

}
