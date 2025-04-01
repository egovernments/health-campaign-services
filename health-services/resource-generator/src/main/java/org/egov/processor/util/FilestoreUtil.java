package org.egov.processor.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.processor.config.Configuration;
import org.egov.processor.repository.ServiceRequestRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.egov.processor.config.ErrorConstants.NOT_ABLE_TO_CONVERT_MULTIPARTFILE_TO_BYTESTREAM_CODE;
import static org.egov.processor.config.ErrorConstants.NOT_ABLE_TO_CONVERT_MULTIPARTFILE_TO_BYTESTREAM_MESSAGE;
import static org.egov.processor.config.ServiceConstants.*;

@Component
@Slf4j
public class FilestoreUtil {

    private Configuration config;

    private ServiceRequestRepository serviceRequestRepository;


    public FilestoreUtil(Configuration config, ServiceRequestRepository serviceRequestRepository) {
        this.config = config;
        this.serviceRequestRepository = serviceRequestRepository;
    }

    /**
     * Retrieves a file from the file store service based on the tenant ID and file store ID.
     *
     * @param tenantId   The ID of the tenant.
     * @param fileStoreId The ID of the file in the file store.
     * @return The file content as a byte array.
     */
    public byte[] getFileByteArray(String tenantId, String fileStoreId) {
        String fileStoreServiceLink = getFileStoreServiceLink(tenantId, fileStoreId);
        byte[] responseInByteArray;
        Object response;
        try {
            response = serviceRequestRepository.fetchResultWithGET(new StringBuilder(fileStoreServiceLink));
            responseInByteArray = (byte[]) response;
        } catch (Exception ex) {
            log.error("File store id response error!!", ex);
            throw new CustomException("FILESTORE_EXCEPTION", "File Store response can not parsed!!!");
        }
        return responseInByteArray;
    }


    /**
     * Uploads a file to the file store service.
     *
     * @param file     The file to upload.
     * @param tenantId The ID of the tenant.
     * @return The file store ID of the uploaded file.
     */
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

    /**
     * Generates the file store service link by combining the file store host and endpoint,
     * and replacing placeholders for tenant ID and file store ID.
     *
     * @param tenantId    The ID of the tenant.
     * @param fileStoreId The ID of the file store.
     * @return The generated file store service link.
     */
    private String getFileStoreServiceLink(String tenantId, String fileStoreId) {
        String fileStoreServiceLink = config.getFileStoreHost() + config.getFileStoreEndpoint();
        fileStoreServiceLink = fileStoreServiceLink.replace(TENANTID_REPLACER, tenantId);
        fileStoreServiceLink = fileStoreServiceLink.replace(FILESTORE_ID_REPLACER, fileStoreId);
        return fileStoreServiceLink;
    }


    /**
     * Reads the content of a file as a byte array.
     *
     * @param file The file to read.
     * @return The file content as a byte array.
     */
    private byte[] readFileContent(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new CustomException("IOException",e.getMessage());
        }
    }

    /**
     * Creates a multipart file from a file and its content.
     *
     * @param file        The file to create a multipart file from.
     * @param fileContent The content of the file as a byte array.
     * @return The created multipart file.
     */
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

    /**
     * Creates HTTP headers for the multipart file upload.
     *
     * @param filename The name of the file.
     * @return The HTTP headers.
     */
    private HttpHeaders createHttpHeaders(String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, String.format("form-data; name=\"file\"; filename=\"%s\"", filename));
        headers.set("Accept", "application/json, text/plain, */*");
        return headers;
    }

    /**
     * Creates the HTTP body for the multipart file upload.
     *
     * @param multipartFile The multipart file to upload.
     * @param tenantId      The ID of the tenant.
     * @return The HTTP body.
     */
    private MultiValueMap<String, Object> createHttpBody(MultipartFile multipartFile, String tenantId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            body.add("file", new HttpEntity<>(multipartFile.getBytes(), createHttpHeaders(multipartFile.getName())));
        } catch (IOException e) {
            throw new CustomException(NOT_ABLE_TO_CONVERT_MULTIPARTFILE_TO_BYTESTREAM_CODE, NOT_ABLE_TO_CONVERT_MULTIPARTFILE_TO_BYTESTREAM_MESSAGE);
        }
        body.add(TENANTID, tenantId);
        body.add(MODULE, MICROPLANNING_MODULE);
        return body;
    }

    /**
     * Extracts the file store ID from the response entity of the file store service.
     *
     * @param responseEntity The response entity from the file store service.
     * @return The file store ID of the uploaded file.
     */
    public String fetchFilestoreIdFromResponse(ResponseEntity<String> responseEntity) {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode;
        try {
            rootNode = objectMapper.readTree(responseEntity.getBody());
        } catch (JsonProcessingException e) {
            log.error(e.getMessage());
            throw new CustomException("FILESTORE_EXCEPTION", "File Store response can not parsed!!!");
        }

        String fileStoreId = rootNode.get(FILES).get(0).get(FILESTORE_ID).asText();
        System.out.println("FileStoreId: " + fileStoreId);
        return fileStoreId;
    }

    /**
     * Retrieves the file extension from a file name.
     *
     * @param file The file to get the extension from.
     * @return The file extension.
     */
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