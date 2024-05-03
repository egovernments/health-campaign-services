package org.egov.processor.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.ResourceMapping;
import org.egov.tracer.model.CustomException;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ParsingUtil {

    private PlanConfigurationUtil planConfigurationUtil;

    private FilestoreUtil filestoreUtil;

    private CalculationUtil calculationUtil;

    public ParsingUtil(PlanConfigurationUtil planConfigurationUtil, FilestoreUtil filestoreUtil, CalculationUtil calculationUtil) {
        this.planConfigurationUtil = planConfigurationUtil;
        this.filestoreUtil = filestoreUtil;
        this.calculationUtil = calculationUtil;
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
            if (attributeName.equalsIgnoreCase("the_geom"))
                continue;
            if (!mappedFromSet.contains(attributeName)) {
                return false;
            }
        }

        return true;
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

    public File getFileFromByteArray(PlanConfiguration planConfig, String fileStoreId) {
        byte[] byteArray = filestoreUtil.getFile(planConfig.getTenantId(), planConfig.getFiles().get(0).getFilestoreId());
        return convertByteArrayToFile(byteArray, "geojson");
    }

    public String convertByteArrayToString(PlanConfiguration planConfig, String fileStoreId) {
        byte[] byteArray = filestoreUtil.getFile(planConfig.getTenantId(), planConfig.getFiles().get(0).getFilestoreId());
        return new String(byteArray, StandardCharsets.UTF_8);
    }

    public String convertFileToJsonString(File geojsonFile) {
        String geoJSONString = null;
        try {
            geoJSONString = new String(Files.readAllBytes(geojsonFile.toPath()));
        } catch (IOException e) {
            throw new CustomException(e.getMessage(), "");
        }

        return geoJSONString;
    }

    public File writeToFile(JsonNode jsonNode, ObjectMapper objectMapper) {
        String outputFileName = "processed.geojson";
        File outputFile;
        try {
            String processedGeoJSON = objectMapper.writeValueAsString(jsonNode);
            Object jsonObject = objectMapper.readValue(processedGeoJSON, Object.class);
            outputFile = new File(outputFileName);
            objectMapper.writeValue(outputFile, jsonObject);
            return outputFile;
        } catch (IOException e) {
            throw new CustomException("NOT_ABLE_TO_WRITE_TO_FILE", "Not able to write processed geojson to file");
        }
    }

    public JsonNode parseJson(String geoJSON, ObjectMapper objectMapper) {
        try {
            return objectMapper.readTree(geoJSON);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException("JSON_PARSE_ERROR", "Error parsing JSON: " + e.getMessage());
        }
    }

    public File extractShapeFilesFromZip(PlanConfiguration planConfig, String fileStoreId, String fileName) throws IOException {
        File shpFile = null;
        byte[] zipFileBytes = filestoreUtil.getFile(planConfig.getTenantId(), planConfig.getFiles().get(0).getFilestoreId());

        try (ByteArrayInputStream bais = new ByteArrayInputStream(zipFileBytes); ZipInputStream zis = new ZipInputStream(bais)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".dbf")) {
                    String fileBaseName = entry.getName().substring(0, entry.getName().length() - 4); // Remove the .shp extension

                    File tempDir = new File(System.getProperty("java.io.tmpdir") + File.separator + fileBaseName);
                    if (!tempDir.exists()) {
                        tempDir.mkdirs();
                    }

                    String shpFilePath = tempDir.getAbsolutePath() + File.separator + entry.getName();
                    FileOutputStream fos = new FileOutputStream(shpFilePath);
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = zis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                    fos.close();

                    shpFile = new File(shpFilePath);
                    break; // Assuming there is only one .shp file in the zip
                }
            }
        }
        return shpFile;
    }

}
