package org.egov.processor.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.ResourceMapping;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ParsingUtil {


    private PlanConfigurationUtil planConfigurationUtil;
    private FilestoreUtil filestoreUtil;

    public ParsingUtil(PlanConfigurationUtil planConfigurationUtil, FilestoreUtil filestoreUtil) {
        this.planConfigurationUtil = planConfigurationUtil;
        this.filestoreUtil = filestoreUtil;
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

    public boolean validateAttributeMapping(List<String> attributeNamesFromFile, List<ResourceMapping> resourceMappingList, String fileStoreId) {
        Set<String> mappedFromSet = resourceMappingList.stream()
                .filter(mapping -> Objects.equals(mapping.getFilestoreId(), fileStoreId))
                .map(ResourceMapping::getMappedFrom)
                .collect(Collectors.toSet());

        //TODO: discuss with dev
//        for (String attributeName : mappedFromSet) {
//            if (!attributeNamesFromFile.contains(attributeName)) {
//                return false;
//            }
//        }

        for (String attributeName : attributeNamesFromFile) {
            if (!mappedFromSet.contains(attributeName)) {
                return false;
            }
        }

        return true;
    }

    public void printFeatureAttributes(SimpleFeature feature, List<String> attributeNames) {
        for (String attributeName : attributeNames) {
            if (attributeName.equals("geometry") || attributeName.equals("the_geom")) continue;
            log.info( attributeName + " - " + feature.getAttribute(attributeName));
        }
        log.info("------------------------------------------");
    }



    public Map<String, Integer> getAttributeNameIndexFromExcel(Sheet sheet) {
        Map<String, Integer> columnIndexMap = new HashMap<>();
        DataFormatter dataFormatter = new DataFormatter();

        // Assuming the first row contains column headers
        Row headerRow = sheet.getRow(0);
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            String columnHeader = dataFormatter.formatCellValue(cell);
            columnIndexMap.put(columnHeader, i);
        }

        return columnIndexMap;
    }

    /**
     * Converts a byte array to a File object.
     *
     * @param byteArray The byte array to convert.
     * @param fileName  The name of the file to create.
     * @return The File object representing the byte array.
     */
    public File convertByteArrayToFile(byte[] byteArray, String fileName) {
        try {
            // Create a new file with the given file name
            File file = new File(fileName);
            // Convert the byte array to a ByteArrayInputStream
            ByteArrayInputStream bis = new ByteArrayInputStream(byteArray);
            // Use Apache Commons IO to copy the ByteArrayInputStream to the File
            FileUtils.copyInputStreamToFile(bis, file);
            // Close the ByteArrayInputStream
            bis.close();
            // Return the File object
            return file;
        } catch (IOException e) {
            log.error("CANNOT_CONVERT_BYTE_ARRAY_TO_FILE", "Cannot convert byte array from response to File object");
        }
        return null;
    }

    public Map<String, String> createMappingMapForFilestoreId(List<ResourceMapping> resourceMappingList, String filestoreId) {
        return resourceMappingList.stream()
                .filter(mapping -> filestoreId.equals(mapping.getFilestoreId()))
                .collect(Collectors.toMap(ResourceMapping::getMappedTo, ResourceMapping::getMappedFrom));
    }

    public File getFileFromByteArray(PlanConfiguration planConfig, String fileStoreId) {
        byte[] byteArray = filestoreUtil.getFile(planConfig.getTenantId(), planConfig.getFiles().get(0).getFilestoreId());
        return convertByteArrayToFile(byteArray, "geojson");
    }

}
