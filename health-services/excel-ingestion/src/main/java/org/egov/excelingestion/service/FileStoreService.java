package org.egov.excelingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.egov.excelingestion.web.models.filestore.FileStoreResponse;
import org.egov.excelingestion.web.models.filestore.FileInfo;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FileStoreService {

    private final RestTemplate restTemplate; // Keep for multipart file upload
    private final ExcelIngestionConfig config;
    private final ObjectMapper objectMapper;
    private final CustomExceptionHandler exceptionHandler;

    public FileStoreService(RestTemplate restTemplate, ExcelIngestionConfig config, ObjectMapper objectMapper,
                           CustomExceptionHandler exceptionHandler) {
        this.restTemplate = restTemplate; // FileStore requires multipart upload, keeping RestTemplate
        this.config = config;
        this.objectMapper = objectMapper;
        this.exceptionHandler = exceptionHandler;
    }

    public String uploadFile(byte[] fileBytes, String tenantId, String fileName) throws IOException {
        String url = config.getFilestoreHost() + config.getFilestoreUploadEndpoint();
        log.info("Uploading file to filestore: {}", url);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        body.add("tenantId", tenantId);
        body.add("module", "excel-ingestion");

        try {
            // Use a simpler approach - create custom RestTemplate with no message converters for response
            RestTemplate customRestTemplate = new RestTemplate();
            
            // Clear all message converters to avoid automatic deserialization
            customRestTemplate.getMessageConverters().clear();
            
            // Add only form converter for request (multipart)
            customRestTemplate.getMessageConverters().add(new FormHttpMessageConverter());
            
            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, requestHeaders);
            
            // Use execute method with ResponseExtractor to get raw response
            String responseBody = customRestTemplate.execute(url, HttpMethod.POST, 
                httpRequest -> {
                    // Write multipart request
                    FormHttpMessageConverter converter = new FormHttpMessageConverter();
                    converter.write(body, MediaType.MULTIPART_FORM_DATA, httpRequest);
                },
                response -> {
                    // Read raw response
                    try (java.io.InputStream inputStream = response.getBody()) {
                        if (inputStream != null) {
                            return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        }
                    }
                    return null;
                });
            
            if (responseBody != null) {
                log.info("FileStore upload response: {}", responseBody);
                
                try {
                    // Try to parse as generic map first
                    @SuppressWarnings("unchecked")
                    Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
                    
                    // Look for common fileStoreId patterns
                    String fileStoreId = extractFileStoreIdFromMap(responseMap);
                    if (fileStoreId != null) {
                        log.info("File uploaded successfully, extracted fileStoreId: {}", fileStoreId);
                        return fileStoreId;
                    }
                    
                    // Try to parse as our FileStoreResponse format
                    FileStoreResponse fileStoreResponse = objectMapper.readValue(responseBody, FileStoreResponse.class);
                    
                    // Check fileStoreIds array
                    if (fileStoreResponse.getFiles() != null && !fileStoreResponse.getFiles().isEmpty()) {
                        FileInfo fileInfo = fileStoreResponse.getFiles().get(0);
                        String fileStoreIdFromArray = fileInfo.getFileStoreId() != null ? fileInfo.getFileStoreId() : fileInfo.getId();
                        log.info("File uploaded successfully, fileStoreId from array: {}", fileStoreIdFromArray);
                        return fileStoreIdFromArray;
                    }
                    
                    // Check if response has file ID mappings
                    if (fileStoreResponse.getFileIdToUrlMap() != null && !fileStoreResponse.getFileIdToUrlMap().isEmpty()) {
                        String fileStoreIdFromMapping = fileStoreResponse.getFileIdToUrlMap().keySet().iterator().next();
                        log.info("File uploaded successfully, fileStoreId from mapping: {}", fileStoreIdFromMapping);
                        return fileStoreIdFromMapping;
                    }
                    
                } catch (Exception parseException) {
                    log.error("Error parsing FileStore response: {}", parseException.getMessage());
                    log.error("Raw response body: {}", responseBody);
                    
                    // Last resort: look for UUID pattern in raw response
                    String uuidPattern = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(uuidPattern);
                    java.util.regex.Matcher matcher = pattern.matcher(responseBody);
                    if (matcher.find()) {
                        String extractedUuid = matcher.group();
                        log.info("File uploaded successfully, extracted UUID from response: {}", extractedUuid);
                        return extractedUuid;
                    }
                }
            }
            
            log.error("Failed to upload file to filestore, response body: {}", responseBody);
            exceptionHandler.throwCustomException(ErrorConstants.FILE_STORE_SERVICE_ERROR, 
                    ErrorConstants.FILE_STORE_SERVICE_ERROR_MESSAGE, 
                    new RuntimeException("FileStore API returned unsuccessful response"));
            return null;
        } catch (Exception e) {
            log.error("Error uploading file to filestore: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.FILE_STORE_SERVICE_ERROR, 
                    ErrorConstants.FILE_STORE_SERVICE_ERROR_MESSAGE, e);
            return null;
        }
    }
    
    /**
     * Extracts fileStoreId from various response formats
     */
    @SuppressWarnings("unchecked")
    private String extractFileStoreIdFromMap(Map<String, Object> responseMap) {
        // Pattern 1: Direct fileStoreId field
        if (responseMap.containsKey("fileStoreId")) {
            return (String) responseMap.get("fileStoreId");
        }
        
        // Pattern 2: files array with fileStoreId
        if (responseMap.containsKey("files")) {
            Object files = responseMap.get("files");
            if (files instanceof List) {
                List<Map<String, Object>> filesList = (List<Map<String, Object>>) files;
                if (!filesList.isEmpty()) {
                    Map<String, Object> firstFile = filesList.get(0);
                    if (firstFile.containsKey("fileStoreId")) {
                        return (String) firstFile.get("fileStoreId");
                    }
                    if (firstFile.containsKey("id")) {
                        return (String) firstFile.get("id");
                    }
                }
            }
        }
        
        // Pattern 3: fileStoreIds array
        if (responseMap.containsKey("fileStoreIds")) {
            Object fileStoreIds = responseMap.get("fileStoreIds");
            if (fileStoreIds instanceof List) {
                List<Map<String, Object>> filesList = (List<Map<String, Object>>) fileStoreIds;
                if (!filesList.isEmpty()) {
                    Map<String, Object> firstFile = filesList.get(0);
                    if (firstFile.containsKey("fileStoreId")) {
                        return (String) firstFile.get("fileStoreId");
                    }
                    if (firstFile.containsKey("id")) {
                        return (String) firstFile.get("id");
                    }
                }
            }
        }
        
        // Pattern 4: Look for UUID-like keys directly in response
        for (Map.Entry<String, Object> entry : responseMap.entrySet()) {
            String key = entry.getKey();
            if (key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                return key; // Return the UUID key itself
            }
        }
        
        return null;
    }
}
