package org.egov.excelingestion.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ServiceRequestRepository error handling
 */
class ServiceRequestRepositoryTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CustomExceptionHandler exceptionHandler;

    private ServiceRequestRepository serviceRequestRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        serviceRequestRepository = new ServiceRequestRepository(objectMapper, restTemplate, exceptionHandler);
    }

    @Test
    void testFetchResult_HttpClientErrorException_PreservesActualErrorResponse() {
        // Given
        StringBuilder uri = new StringBuilder("http://localhost:8084/service");
        Object request = new Object();
        String actualErrorResponse = "{\"ResponseInfo\":null,\"Errors\":[{\"code\":\"SERVICE_DOWN\",\"message\":\"Service unavailable\"}]}";
        
        HttpClientErrorException httpError = HttpClientErrorException.create(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Bad Request", null, actualErrorResponse.getBytes(), null);
        
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(httpError);

        doThrow(new CustomException(ErrorConstants.EXTERNAL_SERVICE_ERROR, actualErrorResponse))
                .when(exceptionHandler).throwCustomException(eq(ErrorConstants.EXTERNAL_SERVICE_ERROR), eq(actualErrorResponse), any(Exception.class));

        // When & Then
        CustomException exception = assertThrows(CustomException.class, () -> {
            serviceRequestRepository.fetchResult(uri, request);
        });

        assertEquals(ErrorConstants.EXTERNAL_SERVICE_ERROR, exception.getCode());
        assertEquals(actualErrorResponse, exception.getMessage());
        
        verify(exceptionHandler).throwCustomException(
                eq(ErrorConstants.EXTERNAL_SERVICE_ERROR), 
                eq(actualErrorResponse), 
                eq(httpError)
        );
    }

    @Test
    void testFetchResult_NetworkException_PreservesActualErrorMessage() {
        // Given
        StringBuilder uri = new StringBuilder("http://localhost:8084/service");
        Object request = new Object();
        String networkErrorMessage = "Connection refused: connect";
        
        ResourceAccessException networkError = new ResourceAccessException(networkErrorMessage, new ConnectException(networkErrorMessage));
        
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(networkError);

        doThrow(new CustomException(ErrorConstants.EXTERNAL_SERVICE_ERROR, networkErrorMessage))
                .when(exceptionHandler).throwCustomException(eq(ErrorConstants.EXTERNAL_SERVICE_ERROR), eq(networkErrorMessage), any(Exception.class));

        // When & Then
        CustomException exception = assertThrows(CustomException.class, () -> {
            serviceRequestRepository.fetchResult(uri, request);
        });

        assertEquals(ErrorConstants.EXTERNAL_SERVICE_ERROR, exception.getCode());
        assertEquals(networkErrorMessage, exception.getMessage());
        
        verify(exceptionHandler).throwCustomException(
                eq(ErrorConstants.EXTERNAL_SERVICE_ERROR), 
                eq(networkErrorMessage), 
                eq(networkError)
        );
    }

    @Test
    void testFetchResult_SuccessfulCall_ReturnsResponse() {
        // Given
        StringBuilder uri = new StringBuilder("http://localhost:8084/service");
        Object request = new Object();
        Map<String, Object> expectedResponse = Map.of("status", "success", "data", "test");
        
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenReturn(expectedResponse);

        // When
        Object result = serviceRequestRepository.fetchResult(uri, request);

        // Then
        assertEquals(expectedResponse, result);
        verifyNoInteractions(exceptionHandler);
    }

    @Test
    void testFetchResult_WithGenericClass_HttpClientErrorException_PreservesError() {
        // Given
        StringBuilder uri = new StringBuilder("http://localhost:8084/service");
        Object request = new Object();
        String actualErrorResponse = "{\"error\":\"MDMS_SERVICE_DOWN\",\"message\":\"MDMS service is not reachable\"}";
        
        HttpClientErrorException httpError = HttpClientErrorException.create(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", null, actualErrorResponse.getBytes(), null);
        
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(httpError);

        doThrow(new CustomException(ErrorConstants.EXTERNAL_SERVICE_ERROR, actualErrorResponse))
                .when(exceptionHandler).throwCustomException(eq(ErrorConstants.EXTERNAL_SERVICE_ERROR), eq(actualErrorResponse), any(Exception.class));

        // When & Then
        CustomException exception = assertThrows(CustomException.class, () -> {
            serviceRequestRepository.fetchResult(uri, request, String.class);
        });

        assertEquals(ErrorConstants.EXTERNAL_SERVICE_ERROR, exception.getCode());
        assertEquals(actualErrorResponse, exception.getMessage());
    }

    @Test
    void testFetchResult_WithGenericClass_SuccessfulCall_ReturnsTypedResponse() {
        // Given
        StringBuilder uri = new StringBuilder("http://localhost:8084/service");
        Object request = new Object();
        String expectedResponse = "success response";
        
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(expectedResponse);

        // When
        String result = serviceRequestRepository.fetchResult(uri, request, String.class);

        // Then
        assertEquals(expectedResponse, result);
        verifyNoInteractions(exceptionHandler);
    }
}