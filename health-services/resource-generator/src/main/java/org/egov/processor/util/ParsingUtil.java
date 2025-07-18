package org.egov.processor.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.poi.ss.usermodel.*;
import org.egov.processor.config.ServiceConstants;
import org.egov.processor.web.models.*;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.egov.processor.config.ErrorConstants.*;
import static org.egov.processor.config.ServiceConstants.*;

@Slf4j
@Component
public class ParsingUtil {

    private FilestoreUtil filestoreUtil;

    private ObjectMapper objectMapper;

    public ParsingUtil(FilestoreUtil filestoreUtil, ObjectMapper objectMapper) {
        this.filestoreUtil = filestoreUtil;
        this.objectMapper = objectMapper;
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
                log.debug("Plan configuration doesn't contain a mapping for attribute -> " + attributeName);
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
        byte[] byteArray = filestoreUtil.getFileByteArray(planConfig.getTenantId(), fileStoreId);
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
        byte[] byteArray = filestoreUtil.getFileByteArray(planConfig.getTenantId(), fileStoreId);
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
        byte[] zipFileBytes = filestoreUtil.getFileByteArray(planConfig.getTenantId(), fileStoreId);

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

    /**
     * Extracts the names of properties defined within the "numberProperties" and "stringProperties" arrays from admin schema
     *
     * @param rootNode The root JSON node from which to extract property names.
     * @return A list of property names found in "numberProperties" and "stringProperties".
     */
    public List<String> extractPropertyNamesFromAdminSchema(JsonNode rootNode) {
        List<String> names = new ArrayList<>();

        // Access the "properties" node directly from the root node
        JsonNode propertiesNode = rootNode.path("properties");

        // Extract names from "numberProperties"
        JsonNode numberProperties = propertiesNode.path("numberProperties");
        if (numberProperties.isArray()) {
            for (JsonNode property : numberProperties) {
                String name = property.path("name").asText(null);
                if (name != null) {
                    names.add(name);
                }
            }
        }

        // Extract names from "stringProperties"
        JsonNode stringProperties = propertiesNode.path("stringProperties");
        if (stringProperties.isArray()) {
            for (JsonNode property : stringProperties) {
                String name = property.path("name").asText(null);
                if (name != null) {
                    names.add(name);
                }
            }
        }

        return names;
    }


    /**
     * Checks if a given row is empty.
     *
     * A row is considered empty if it is null or if all of its cells are empty or of type BLANK.
     *
     * @param row the Row to check
     * @return true if the row is empty, false otherwise
     */
    public boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    /**
     * Retrieves the index value of the boundary code from the sorted column list based on the mapped values.
     *
     * @param indexValue The initial index value.
     * @param sortedColumnList The sorted list of column names and indices.
     * @param mappedValues The map containing mapped values.
     * @return The index value of the boundary code.
     */
    public Integer getIndexOfBoundaryCode(Integer indexValue, List<Map.Entry<String, Integer>> sortedColumnList,Map<String, String> mappedValues) {
        for (Map.Entry<String, Integer> entry : sortedColumnList) {
            if (entry.getKey().equals(mappedValues.get(ServiceConstants.BOUNDARY_CODE))) {
                indexValue = entry.getValue();
            }
        }
        return indexValue;
    }

    /**
     * Sorts the column names and indices based on the provided map of column names and indices.
     *
     * @param mapOfColumnNameAndIndex The map containing column names and their corresponding indices.
     * @return The sorted list of column names and indices.
     */
    public List<Map.Entry<String, Integer>> sortColumnByIndex(Map<String, Integer> mapOfColumnNameAndIndex) {
        List<Map.Entry<String, Integer>> sortedColumnList = new ArrayList<>(mapOfColumnNameAndIndex.entrySet());
        Collections.sort(sortedColumnList, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
                return o1.getValue().compareTo(o2.getValue());
            }
        });
        return sortedColumnList;
    }

    public void printRow(Sheet sheet, Row row) {
        System.out.print("Row -> ");
        for (Cell cell : row) {
            int columnIndex = cell.getColumnIndex();
            switch (cell.getCellType()) {
                case STRING:
                    System.out.print(cell.getStringCellValue() + "\t");
                    break;
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        System.out.print(cell.getDateCellValue() + "\t");
                    } else {
                        System.out.print(cell.getNumericCellValue() + "\t");
                    }
                    break;
                case BOOLEAN:
                    System.out.print(cell.getBooleanCellValue() + "\t");
                    break;
                case FORMULA:
                    System.out.print(cell.getCellFormula() + "\t");
                    break;
                case BLANK:
                    System.out.print("<blank>\t");
                    break;
                default:
                    System.out.print("<unknown>\t");
                    break;
            }
        }
        System.out.println(); // Move to the next line after printing the row
    }

    /**
     * Extracts a specific field from a given JSON object and converts it into the specified return type.
     *
     * @param <T>           The expected return type of the extracted field.
     * @param additionalDetails The JSON object from which the field is to be extracted.
     * @param fieldToExtract The key of the field to extract from the JSON object.
     * @param returnType     The class type of the expected return value.
     * @return The extracted value cast to the specified return type, or {@code null} if the field is missing or not compatible.
     * @throws CustomException If the field is not found or if an error occurs during extraction.
     */
    public <T> T extractFieldsFromJsonObject(Object additionalDetails, String fieldToExtract, Class<T> returnType) {
        try {
            String jsonString = objectMapper.writeValueAsString(additionalDetails);
            JsonNode rootNode = objectMapper.readTree(jsonString);
            JsonNode node = rootNode.get(fieldToExtract);

            if (node != null && !node.isNull()) {
                // Handle List<String> case separately
                if (returnType == List.class && node.isArray()) {
                    List<String> list = new ArrayList<>();
                    for (JsonNode idNode : node) {
                        list.add(idNode.asText());
                    }
                    return returnType.cast(list);
                }

                // Check for different types of JSON nodes
                if (returnType == BigDecimal.class && (node.isDouble() || node.isFloat() || node.isLong() || node.isInt())) {
                    return returnType.cast(BigDecimal.valueOf(node.asDouble()));
                } else if (returnType == Boolean.class && node.isBoolean()) {
                    return returnType.cast(node.asBoolean());
                } else if (returnType == String.class && node.isTextual()) {
                    return returnType.cast(node.asText());
                }
            }
            return null;
        } catch (Exception e) {
            log.error(e.getMessage() + fieldToExtract);
            throw new CustomException(PROVIDED_KEY_IS_NOT_PRESENT_IN_JSON_OBJECT_CODE, PROVIDED_KEY_IS_NOT_PRESENT_IN_JSON_OBJECT_MESSAGE + fieldToExtract);
        }
    }

    /**
     * Adds or updates the value of provided field in the additional details object.
     * @param additionalDetails
     * @param fieldsToBeUpdated
     * @return
     */
    public Map<String, Object> updateFieldInAdditionalDetails(Object additionalDetails, Map<String, Object> fieldsToBeUpdated) {
        try {

            // Get or create the additionalDetails as an ObjectNode
            ObjectNode objectNode = (additionalDetails == null || additionalDetails instanceof NullNode)
                    ? objectMapper.createObjectNode()
                    : objectMapper.convertValue(additionalDetails, ObjectNode.class);

            // Update or add the field in additional details object
            fieldsToBeUpdated.forEach((key, value) -> objectNode.set(key, objectMapper.valueToTree(value)));

            // Convert updated ObjectNode back to a Map
            return objectMapper.convertValue(objectNode, Map.class);

        } catch (Exception e) {
            throw new CustomException(ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_CODE, ERROR_WHILE_UPDATING_ADDITIONAL_DETAILS_MESSAGE + e);
        }
    }

    /**
     * Converts the provided workbook to XLS format.
     *
     * @param workbook The workbook to convert.
     * @return The converted XLS file, or null if an error occurred.
     */
    public File convertWorkbookToXls(Workbook workbook) {
        try {
            // Create a temporary file for the output XLS file
            File outputFile = File.createTempFile("output", ".xls");

            // Write the XLS file
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
                log.info("XLS file saved successfully.");
                return outputFile;
            } catch (IOException e) {
                log.error(ERROR_SAVING_EXCEL_FILE + LOG_PLACEHOLDER, e.getMessage());
            }
        } catch (IOException e) {
            log.error(ERROR_CONVERTING_TO_EXCEL_FILE + LOG_PLACEHOLDER, e.getMessage());
        }
        return null;
    }
}
