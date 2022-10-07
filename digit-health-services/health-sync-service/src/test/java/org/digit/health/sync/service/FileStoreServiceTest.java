package org.digit.health.sync.service;

import org.digit.health.sync.repository.ServiceRequestRepository;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class FileStoreServiceTest {

    @Mock
    private ServiceRequestRepository serviceRequestRepository;

    @InjectMocks
    private FileStoreService fileStoreService;

    @BeforeEach
    void setUp() {
        fileStoreService = new FileStoreService(serviceRequestRepository);
        ReflectionTestUtils.setField(fileStoreService, "fileStoreServiceHost",
                "http://localhost:8083");
    }

    @Test
    @DisplayName("given file store id and tenant id should get response")
    void givenFileStoreIdAndTenantIdShouldGetResponse() {
        String fileStoreId = "fileStoreId";
        String tenantId = "tenantId";
        byte[] response = new byte[]{};
        Mockito.when(serviceRequestRepository.fetchResult(any(), any()))
                .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        assertEquals(fileStoreService.getFile(fileStoreId, tenantId), response);
    }

    @Test
    @DisplayName("should throw exception if file store service call fails")
    void shouldThrowExceptionIfFileStoreServiceCallFails() {
        String fileStoreId = "fileStoreId";
        String tenantId = "tenantId";
        Mockito.when(serviceRequestRepository.fetchResult(any(), any()))
                .thenThrow(new RestClientException("Error"));

        assertThrows(RestClientException.class, () -> fileStoreService
                .getFile(fileStoreId, tenantId));
    }

}