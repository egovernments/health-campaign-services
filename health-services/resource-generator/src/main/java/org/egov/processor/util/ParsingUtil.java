package org.egov.processor.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.node.DecimalNode;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.*;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.ResourceMapping;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

import static org.egov.processor.config.ServiceConstants.PROPERTIES;

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

    public List<String> fetchAttributeNamesFromJson(JsonNode jsonNode)
    {
        if(jsonNode.get("features") == null)
            throw new CustomException("No Features found in geojson", " ");
        List<String> columnNames = new ArrayList<>();       
        JsonNode propertiesNode = jsonNode.get("features").get(0).get("properties");
        Iterator<String> fieldNames = propertiesNode.fieldNames();
        while (fieldNames.hasNext()) {
            String columnName = fieldNames.next();
            columnNames.add(columnName);
        }       
        return columnNames;
    }


    public void validateColumnNames(List<String> columnNamesList, PlanConfiguration planConfig, String fileStoreId ) {
        Set<String> mappedFromSet = planConfig.getResourceMapping().stream()
                .filter(mapping -> Objects.equals(mapping.getFilestoreId(), fileStoreId))
                .map(ResourceMapping::getMappedFrom)
                .collect(Collectors.toSet());

        for (String attributeName : mappedFromSet) {
            if (attributeName.equalsIgnoreCase("the_geom"))
                continue;
            if (!columnNamesList.contains(attributeName)) {
                log.error("Attribute mapping is invalid.");
                log.info("Plan configuration doesn't contain a mapping for attribute -> " + attributeName);
                throw new CustomException("Attribute mapping is invalid.", "Plan configuration doesn't contain a mapping for attribute -> " + attributeName);
            }
        }

        log.info("Attribute mapping is valid.");
    }

    /**
     * Extracts attribute names and their corresponding indices from the first row of an Excel sheet.
     *
     * @param sheet The Excel sheet from which to extract attribute names and indices.
     * @return A sorted map containing attribute names as keys and their corresponding indices as values.
     */
    public Map<String, Integer> getAttributeNameIndexFromExcel(Sheet sheet) {
        Map<String, Integer> columnIndexMap = new HashMap<>();
        Map<String, Integer> sortedMap = new LinkedHashMap<>();
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
     * Retrieves the mapped value from the feature JSON node using the mapped value for the given input,
     * returning it as the appropriate data type.
     *
     * @param input        The input value.
     * @param feature      The feature JSON node.
     * @param mappedValues The mapped values.
     * @return The value of the corresponding data type (Long, String, Boolean, etc.).
     * @throws CustomException if the input value is not found in the feature JSON node or if the value type is unsupported.
     */
    public Object extractMappedValueFromFeatureForAnInput(String input, JsonNode feature, Map<String, String> mappedValues) {
        // Get the value as a JsonNode, not a String
        JsonNode mappedValueNode = feature.get(PROPERTIES).get(mappedValues.get(input));

        // Check if the value exists in the JSON
        if (!ObjectUtils.isEmpty(mappedValueNode)) {

            // Now return the value based on its actual type in the JsonNode
            if (mappedValueNode instanceof DecimalNode) {
                return ((DecimalNode) mappedValueNode).decimalValue(); // Returns BigDecimal
            } else if (mappedValueNode.isBoolean()) {
                return mappedValueNode.asBoolean();
            } else if (mappedValueNode.isTextual()) {
                return mappedValueNode.asText();
            } else {
                return null;
            }
        }
        else {
            return null;
        }
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
            File file = new File(fileName);
            ByteArrayInputStream bis = new ByteArrayInputStream(byteArray);
            FileUtils.copyInputStreamToFile(bis, file);
            bis.close();
            return file;
        } catch (IOException e) {
            log.error("CANNOT_CONVERT_BYTE_ARRAY_TO_FILE", "Cannot convert byte array from response to File object");
        }
        return null;
    }

    /**
     * Retrieves a file from a byte array.
     *
     * @param planConfig  The plan configuration containing tenant and file information.
     * @param fileStoreId The ID of the file store.
     * @return The File object representing the byte array.
     */
    public File getFileFromByteArray(PlanConfiguration planConfig, String fileStoreId) {
        byte[] byteArray = filestoreUtil.getFile(planConfig.getTenantId(), fileStoreId);
        return convertByteArrayToFile(byteArray, "geojson");
    }

    /**
     * Converts a byte array to a String.
     *
     * @param planConfig  The plan configuration containing tenant and file information.
     * @param fileStoreId The ID of the file store.
     * @return The String representation of the byte array.
     */
    public String convertByteArrayToString(PlanConfiguration planConfig, String fileStoreId) {
        byte[] byteArray = filestoreUtil.getFile(planConfig.getTenantId(), fileStoreId);
        return new String(byteArray, StandardCharsets.UTF_8);
    }

    /**
     * Converts a File object containing JSON data to a String.
     *
     * @param geojsonFile The File object containing JSON data.
     * @return The String representation of the JSON data.
     */
    public String convertFileToJsonString(File geojsonFile) {
        String geoJSONString = null;
        try {
            geoJSONString = new String(Files.readAllBytes(geojsonFile.toPath()));
        } catch (IOException e) {
            throw new CustomException(e.getMessage(), "");
        }

        return geoJSONString;
    }

    /**
     * Writes a JsonNode to a file.
     *
     * @param jsonNode     The JsonNode to write.
     * @param objectMapper The ObjectMapper used for writing the JsonNode.
     * @return The File object representing the written JSON data.
     */
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

    /**
     * Parses a JSON string into a JsonNode.
     *
     * @param geoJSON      The JSON string to parse.
     * @param objectMapper The ObjectMapper used for parsing the JSON string.
     * @return The parsed JsonNode.
     */
    public JsonNode parseJson(String geoJSON, ObjectMapper objectMapper) {
        try {
            return objectMapper.readTree(geoJSON);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException("JSON_PARSE_ERROR", "Error parsing JSON: " + e.getMessage());
        }
    }

    /**
     * Extracts shapefiles from a zip file and returns the .shp file.
     *
     * @param planConfig  The plan configuration containing tenant and file information.
     * @param fileStoreId The ID of the file store.
     * @param fileName    The name of the file to extract.
     * @return The extracted .shp File object.
     * @throws IOException If an I/O error occurs while extracting the shapefiles.
     */
    public File extractShapeFilesFromZip(PlanConfiguration planConfig, String fileStoreId, String fileName) throws IOException {
        File shpFile = null;
        byte[] zipFileBytes = filestoreUtil.getFile(planConfig.getTenantId(), fileStoreId);

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
