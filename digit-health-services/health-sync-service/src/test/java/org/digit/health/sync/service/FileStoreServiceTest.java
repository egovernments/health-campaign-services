package org.digit.health.sync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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
    @DisplayName("given file store id and tenant id should get response")
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
    @DisplayName("should throw exception if file store service call fails")
    public void shouldThrowExceptionIfFileStoreServiceCallFails() {
        String fileStoreId = "fileStoreId";
        String tenantId = "tenantId";

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_OCTET_STREAM));
        HttpEntity<String> entity = new HttpEntity<>(headers);
        Mockito.when(restTemplate.exchange(String.format("http://localhost:8083/filestore/v1/files/id?tenantId=%s&fileStoreId=%s",tenantId,fileStoreId), HttpMethod.GET, entity, byte[].class))
                .thenThrow(new RestClientException("Error"));

        assertThrows(RestClientException.class, () -> fileStoreService.getFile(fileStoreId, tenantId));
    }

}