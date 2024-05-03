package org.egov.processor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.egov.processor.util.CalculationUtil;
import org.egov.processor.util.FilestoreUtil;
import org.egov.processor.util.ParsingUtil;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.ResourceMapping;
import org.egov.tracer.model.CustomException;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.geojson.feature.FeatureJSON;
import org.springframework.stereotype.Service;

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

    @Override
    public Object parseFileData(PlanConfiguration planConfig, String fileStoreId) {
        File geojsonFile = convertShapefileToGeoJson(planConfig, fileStoreId);
        String geoJSONString = parsingUtil.convertFileToJsonString(geojsonFile);
        JsonNode jsonNode = parsingUtil.parseJson(geoJSONString, objectMapper);

        Map<String, BigDecimal> resultMap = new HashMap<>();
        Map<String, String> mappedValues = planConfig.getResourceMapping().stream().collect(Collectors.toMap(ResourceMapping::getMappedTo, ResourceMapping::getMappedFrom));
        Map<String, BigDecimal> assumptionValueMap = calculationUtil.convertAssumptionsToMap(planConfig.getAssumptions());

        calculationUtil.calculateResources(jsonNode, planConfig.getOperations(), resultMap, mappedValues, assumptionValueMap);

        File updatedGeojsonFile = parsingUtil.writeToFile(jsonNode, objectMapper);

        return filestoreUtil.uploadFile(updatedGeojsonFile, planConfig.getTenantId());
    }

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
            throw new CustomException(e.getMessage(),"");
        }

        return geojsonFile;
    }

    private DataStore getDataStore(File shapefile) throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("url", shapefile.toURI().toURL());
        return DataStoreFinder.getDataStore(params);
    }

    private void writeFeaturesToGeoJson(SimpleFeatureSource featureSource, File geojsonFile) throws IOException {
        try (FileOutputStream geojsonStream = new FileOutputStream(geojsonFile)) {
            new FeatureJSON().writeFeatureCollection(featureSource.getFeatures(), geojsonStream);
        }
    }

}
