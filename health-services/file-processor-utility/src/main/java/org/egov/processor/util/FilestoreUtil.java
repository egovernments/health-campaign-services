package org.egov.processor.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.egov.processor.config.Configuration;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.processor.web.models.PlanConfigurationResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.mock.web.MockMultipartFile;

import static org.egov.processor.config.ServiceConstants.ERROR_WHILE_FETCHING_FROM_PLAN_SERVICE;
import static org.egov.processor.config.ServiceConstants.FILES;
import static org.egov.processor.config.ServiceConstants.FILESTORE_ID;
import static org.egov.processor.config.ServiceConstants.FILESTORE_ID_REPLACER;
import static org.egov.processor.config.ServiceConstants.MICROPLANNING_MODULE;
import static org.egov.processor.config.ServiceConstants.TENANTID_REPLACER;

@Component
@Slf4j
public class FilestoreUtil {

    private Configuration config;

    private ServiceRequestRepository serviceRequestRepository;

    private ObjectMapper mapper;

    private RestTemplate restTemplate;

    public FilestoreUtil(Configuration config, ServiceRequestRepository serviceRequestRepository, ObjectMapper mapper, RestTemplate restTemplate) {
        this.config = config;
        this.serviceRequestRepository = serviceRequestRepository;
        this.mapper = mapper;
        this.restTemplate = restTemplate;
    }

    public byte[] getFile(String tenantId, String fileStoreId) {
        String fileStoreServiceLink = config.getFileStoreHost() + config.getFileStoreEndpoint();
        fileStoreServiceLink = fileStoreServiceLink.replace(TENANTID_REPLACER, tenantId);
        fileStoreServiceLink = fileStoreServiceLink.replace(FILESTORE_ID_REPLACER, fileStoreId);
        byte[] responseInByteArray = null;
        Object response = null;
        try {
            response = serviceRequestRepository.fetchResultWithGET(new StringBuilder(fileStoreServiceLink));
            responseInByteArray = (byte[]) response;
        } catch (Exception ex) {
            log.error("File store id response error!!", ex);
            throw new CustomException("FILESTORE_EXCEPTION", "File Store response can not parsed!!!");
        }
        return responseInByteArray;
    }

    public String uploadFile(File file, String tenantId) {
        byte[] fileContent = readFileContent(file);
        MultipartFile multipartFile = createMultipartFile(file, fileContent);
        String url = config.getFileStoreHost() + config.getFileStoreUploadEndpoint();
        HttpHeaders headers = createHttpHeaders(multipartFile.getName());
        MultiValueMap<String, Object> body = createHttpBody(multipartFile, tenantId);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> responseEntity = serviceRequestRepository.sendHttpRequest(url, requestEntity);
        return fetchFilestoreIdFromResponse(responseEntity);
    }

    private byte[] readFileContent(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private MultipartFile createMultipartFile(File file, byte[] fileContent) {
        List<String> excelExtensions = Arrays.asList("xls", "xlsx");
        String fileExtension = getFileExtension(file);
        MultipartFile multipartFile = null;
        if (excelExtensions.contains(fileExtension)) {
            multipartFile = new MockMultipartFile(file.getName(), file.getName(), "application/xls", fileContent);
        }
        else
            multipartFile = new MockMultipartFile(file.getName(), file.getName(), "application/geo+json", fileContent);

        return multipartFile;
    }

    private MultipartFile createMultipartFileForExcel(File file, byte[] fileContent) {
        return new MockMultipartFile(file.getName(), file.getName(), "application/xls", fileContent);
    }


    private HttpHeaders createHttpHeaders(String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, String.format("form-data; name=\"file\"; filename=\"%s\"", filename));
        headers.set("Accept", "application/json, text/plain, */*");
        return headers;
    }

    private MultiValueMap<String, Object> createHttpBody(MultipartFile multipartFile, String tenantId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            body.add("file", new HttpEntity<>(multipartFile.getBytes(), createHttpHeaders(multipartFile.getName())));
        } catch (IOException e) {
            throw new CustomException("NOT_ABLE_TO_CONVERT_MULTIPARTFILE_TO_BYTESTREAM", "Not able to fetch byte stream from a multipart file");
        }
        body.add("tenantId", tenantId);
        body.add("module", MICROPLANNING_MODULE);
        return body;
    }


    public String fetchFilestoreIdFromResponse(ResponseEntity<String> responseEntity) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = null;
        try {
            rootNode = objectMapper.readTree(responseEntity.getBody());
        } catch (JsonProcessingException e) {
            log.error(e.getMessage());
            throw new CustomException("FILESTORE_EXCEPTION", "File Store response can not parsed!!!");
        }

        // Assuming FILES and FILESTORE_ID are constants representing the JSON keys
        String fileStoreId = rootNode.get("files").get(0).get("fileStoreId").asText();
        System.out.println("FileStoreId: " + fileStoreId);
        return fileStoreId;
    }

    public String getFileExtension(File file) {
        String fileName = file.getName();
        String extension = "";

        int lastIndexOfDot = fileName.lastIndexOf('.');
        if (lastIndexOfDot >= 0) {
            extension = fileName.substring(lastIndexOfDot + 1);
        }

        return extension;
    }

}