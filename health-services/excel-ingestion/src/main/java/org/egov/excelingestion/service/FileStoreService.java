package org.egov.excelingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.egov.common.http.client.ServiceRequestClient;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FileStoreService {

    private final RestTemplate restTemplate; // Keep for multipart file upload
    private final ExcelIngestionConfig config;
    private final ObjectMapper objectMapper;
    
    @Autowired
    private CustomExceptionHandler exceptionHandler;

    public FileStoreService(RestTemplate restTemplate, ExcelIngestionConfig config, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate; // FileStore requires multipart upload, keeping RestTemplate
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public String uploadFile(byte[] fileBytes, String tenantId, String fileName) throws IOException {
        String url = config.getFilestoreHost() + config.getFilestoreUploadEndpoint();
        log.info("Uploading file to filestore: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        body.add("tenantId", tenantId);
        body.add("module", "excel-ingestion");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);
            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.CREATED) {
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null && responseBody.containsKey("files")) {
                    List<Map<String, Object>> files = (List<Map<String, Object>>) responseBody.get("files");
                    if (!files.isEmpty() && files.get(0).containsKey("fileStoreId")) {
                        String fileStoreId = (String) files.get(0).get("fileStoreId");
                        log.info("File uploaded successfully, fileStoreId: {}", fileStoreId);
                        return fileStoreId;
                    }
                }
            }
            log.error("Failed to upload file to filestore, response: {}", response.getBody());
            exceptionHandler.throwCustomException(ErrorConstants.FILE_STORE_SERVICE_ERROR, 
                    ErrorConstants.FILE_STORE_SERVICE_ERROR_MESSAGE, 
                    new RuntimeException("FileStore API returned unsuccessful response: " + response.getBody()));
            return null; // This will never be reached due to exception throwing above
        } catch (Exception e) {
            log.error("Error uploading file to filestore: {}", e.getMessage());
            exceptionHandler.throwCustomException(ErrorConstants.FILE_STORE_SERVICE_ERROR, 
                    ErrorConstants.FILE_STORE_SERVICE_ERROR_MESSAGE, e);
            return null; // This will never be reached due to exception throwing above
        }
    }
}
