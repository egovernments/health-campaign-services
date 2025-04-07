package org.egov.processor.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.egov.processor.util.CalculationUtil;
import org.egov.processor.util.FilestoreUtil;
import org.egov.processor.util.ParsingUtil;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationRequest;
import org.egov.processor.web.models.ResourceMapping;
import org.egov.processor.web.models.campaignManager.CampaignResponse;
import org.egov.tracer.model.CustomException;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.geojson.feature.FeatureJSON;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ShapeFileParser implements FileParser {

    private ParsingUtil parsingUtil;

    private FilestoreUtil filestoreUtil;

    private CalculationUtil calculationUtil;

    private ObjectMapper objectMapper;

    public ShapeFileParser(ParsingUtil parsingUtil, FilestoreUtil filestoreUtil, CalculationUtil calculationUtil, ObjectMapper objectMapper) {
        this.parsingUtil = parsingUtil;
        this.filestoreUtil = filestoreUtil;
        this.calculationUtil = calculationUtil;
        this.objectMapper = objectMapper;
    }

    /**
     * Parses the file data based on the provided plan configuration and file store ID.
     * Converts a Shapefile to GeoJSON format, calculates resources based on the operations
     * defined in the plan configuration, and uploads the updated GeoJSON file to the file store.
     *
     * @param planConfigurationRequest  The plan configuration containing mapping and operation details.
     * @param fileStoreId The file store ID of the Shapefile to be converted and parsed.
     * @return The file store ID of the uploaded updated file, or null if an error occurred.
     */
    @Override
    public Object parseFileData(PlanConfigurationRequest planConfigurationRequest, String fileStoreId, CampaignResponse campaignResponse) {
    	PlanConfiguration planConfig = planConfigurationRequest.getPlanConfiguration();
        File geojsonFile = convertShapefileToGeoJson(planConfig, fileStoreId);
        String geoJSONString = parsingUtil.convertFileToJsonString(geojsonFile);
        JsonNode jsonNode = parsingUtil.parseJson(geoJSONString, objectMapper);

        List<String> columnNamesList = parsingUtil.fetchAttributeNamesFromJson(jsonNode);
        parsingUtil.validateColumnNames(columnNamesList, planConfig, fileStoreId);

        Map<String, BigDecimal> resultMap = new HashMap<>();
        Map<String, String> mappedValues = planConfig.getResourceMapping().stream()
        		.filter(f-> f.getFilestoreId().equals(fileStoreId))
        		.collect(Collectors.toMap(ResourceMapping::getMappedTo, ResourceMapping::getMappedFrom));
        Map<String, BigDecimal> assumptionValueMap = calculationUtil.convertAssumptionsToMap(planConfig.getAssumptions());

        calculationUtil.calculateResources(jsonNode, planConfigurationRequest, resultMap, mappedValues, assumptionValueMap);

        File updatedGeojsonFile = parsingUtil.writeToFile(jsonNode, objectMapper);

        return filestoreUtil.uploadFile(updatedGeojsonFile, planConfig.getTenantId());
    }

    /**
     * Converts a Shapefile to GeoJSON format and writes it to a GeoJSON file.
     *
     * @param planConfig  The plan configuration containing mapping details.
     * @param fileStoreId The file store ID of the Shapefile to be converted.
     * @return The GeoJSON file containing the converted data.
     */
    public File convertShapefileToGeoJson(PlanConfiguration planConfig, String fileStoreId) {
        File shapefile = null;
        try {
            shapefile = parsingUtil.extractShapeFilesFromZip(planConfig, fileStoreId, "shapefile");
        } catch (IOException exception) {
            log.error(exception.getMessage());
        }

        File geojsonFile = new File("geojsonfile.geojson");

        SimpleFeatureSource featureSource = null;
        DataStore dataStore;
        try {
            dataStore = getDataStore(shapefile);
            String typeName = dataStore.getTypeNames()[0];
            featureSource = dataStore.getFeatureSource(typeName);

            writeFeaturesToGeoJson(featureSource, geojsonFile);
        } catch (IOException e) {
            throw new CustomException("ERROR_IN_SHAPE_FILE_PARSER_WHILE_CONVERTING_SHAPE_FILE_TO_GEOJSON_IN_METHOD_CONVERTSHAPEFILETOGEOJSON",e.getMessage());
        }

        return geojsonFile;
    }

    /**
     * Retrieves a DataStore object for a given Shapefile.
     *
     * @param shapefile The Shapefile to retrieve the DataStore for.
     * @return The DataStore object for the Shapefile.
     * @throws IOException If an I/O error occurs.
     */
    private DataStore getDataStore(File shapefile) {
        Map<String, Object> params = new HashMap<>();
        try {
            params.put("url", shapefile.toURI().toURL());
            return DataStoreFinder.getDataStore(params);
        } catch (IOException e) {
        	throw new CustomException("Exception accours while getting data store",e.getMessage());
        }

    }

    /**
     * Writes features from a SimpleFeatureSource to a GeoJSON file.
     *
     * @param featureSource The SimpleFeatureSource containing the features to write.
     * @param geojsonFile   The GeoJSON file to write the features to.
     * @throws IOException If an I/O error occurs.
     */
    private void writeFeaturesToGeoJson(SimpleFeatureSource featureSource, File geojsonFile)  {
        try (FileOutputStream geojsonStream = new FileOutputStream(geojsonFile)) {
            new FeatureJSON().writeFeatureCollection(featureSource.getFeatures(), geojsonStream);
        } catch (IOException e) {
            throw new CustomException("Failed to write feature to GeoJson",e.getMessage());
        }
    }

}
