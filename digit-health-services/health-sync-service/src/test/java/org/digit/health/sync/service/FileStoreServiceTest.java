package org.digit.health.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.digit.health.sync.repository.ServiceRequestRepository;
import org.digit.health.sync.service.checksum.MD5Checksum;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FileStoreServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private FileStoreService fileStoreService;

    @BeforeEach
    void setUp() {
        fileStoreService = new FileStoreService(restTemplate);
        ReflectionTestUtils.setField(fileStoreService, "fileStoreServiceHost", "http://localhost:8083");
    }

    @Test
    @DisplayName("Given File Store Id And Tenant Id Should Get Response")
    public void givenFileStoreIdAndTenantIdShouldGetResponse() {
        String fileStoreId = "fileStoreId";
        String tenantId = "tenantId";
        byte[] response = new byte[] {};

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));
        HttpEntity<String> entity = new HttpEntity<>(headers);
        Mockito.when(restTemplate.exchange(String.format("http://localhost:8083/filestore/v1/files/id?tenantId=%s&fileStoreId=%s",tenantId,fileStoreId), HttpMethod.GET, entity, byte[].class))
          .thenReturn(new ResponseEntity( response, HttpStatus.OK));

        assertEquals(fileStoreService.getFile(fileStoreId, tenantId), response);
    }

    @Test
    @DisplayName("Should Throw Exception if File Store Service Call Fails")
    public void shouldThrowExceptionIfFileStoreServiceCallFails() {
        String fileStoreId = "fileStoreId";
        String tenantId = "tenantId";
        byte[] response = new byte[] {};

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));
        HttpEntity<String> entity = new HttpEntity<>(headers);
        Mockito.when(restTemplate.exchange(String.format("http://localhost:8083/filestore/v1/files/id?tenantId=%s&fileStoreId=%s",tenantId,fileStoreId), HttpMethod.GET, entity, byte[].class))
                .thenThrow(new RestClientException("Error"));

        assertThrows(RestClientException.class, () -> fileStoreService.getFile(fileStoreId, tenantId));
    }

}