package org.egov.excelingestion.service;

import org.egov.common.producer.Producer;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.SheetDataTempRepository;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.SheetDataSearchRequest;
import org.egov.excelingestion.web.models.SheetDataSearchCriteria;
import org.egov.excelingestion.web.models.SheetDataSearchResponse;
import org.egov.excelingestion.web.models.SheetDataDeleteRequest;
import org.egov.excelingestion.web.models.SheetDataDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for SheetDataService covering search and delete functionality
 */
@ExtendWith(MockitoExtension.class)
class SheetDataServiceTest {

    @Mock
    private SheetDataTempRepository repository;

    @Mock
    private Producer producer;

    @Mock
    private CustomExceptionHandler exceptionHandler;

    private SheetDataService sheetDataService;

    @BeforeEach
    void setUp() {
        sheetDataService = new SheetDataService(repository, producer, exceptionHandler);
    }

    @Test
    void testSearchSheetData_Success() throws Exception {
        // Given
        String tenantId = "pg.citya";
        String referenceId = "ref123";
        String fileStoreId = "file456";
        
        RequestInfo requestInfo = RequestInfo.builder()
                .apiId("api123")
                .ver("1.0")
                .ts(System.currentTimeMillis())
                .build();
                
        SheetDataSearchCriteria criteria = SheetDataSearchCriteria.builder()
                .tenantId(tenantId)
                .referenceId(referenceId)
                .fileStoreId(fileStoreId)
                .limit(10)
                .offset(0)
                .build();
                
        SheetDataSearchRequest request = SheetDataSearchRequest.builder()
                .requestInfo(requestInfo)
                .sheetDataSearchCriteria(criteria)
                .build();

        List<Map<String, Object>> mockData = new ArrayList<>();
        Map<String, Object> row1 = new HashMap<>();
        row1.put("name", "John");
        row1.put("age", 25);
        mockData.add(row1);

        when(repository.searchSheetData(eq(tenantId), eq(referenceId), eq(fileStoreId), 
                isNull(), eq(10), eq(0))).thenReturn(mockData);
        when(repository.countSheetData(eq(tenantId), eq(referenceId), eq(fileStoreId), 
                isNull())).thenReturn(1);

        // When
        SheetDataSearchResponse response = sheetDataService.searchSheetData(request);

        // Then
        assertNotNull(response);
        assertNotNull(response.getResponseInfo());
        assertEquals("successful", response.getResponseInfo().getStatus());
        
        SheetDataDetails details = response.getSheetDataDetails();
        assertNotNull(details);
        assertEquals(1, details.getData().size());
        assertEquals(1, details.getTotalCount());
        assertEquals("John", details.getData().get(0).get("name"));
    }

    @Test
    void testSearchSheetData_WithSheetWiseCounts() throws Exception {
        // Given
        String tenantId = "pg.citya";
        String referenceId = "ref123";
        String fileStoreId = "file456";
        
        RequestInfo requestInfo = RequestInfo.builder()
                .apiId("api123")
                .ver("1.0")
                .ts(System.currentTimeMillis())
                .build();
                
        SheetDataSearchCriteria criteria = SheetDataSearchCriteria.builder()
                .tenantId(tenantId)
                .referenceId(referenceId)
                .fileStoreId(fileStoreId)
                .build();
                
        SheetDataSearchRequest request = SheetDataSearchRequest.builder()
                .requestInfo(requestInfo)
                .sheetDataSearchCriteria(criteria)
                .build();

        List<Map<String, Object>> mockData = new ArrayList<>();
        List<Map<String, Object>> mockSheetWiseCounts = new ArrayList<>();
        Map<String, Object> sheetCount = new HashMap<>();
        sheetCount.put("sheetName", "Sheet1");
        sheetCount.put("recordCount", 5);
        mockSheetWiseCounts.add(sheetCount);

        when(repository.searchSheetData(eq(tenantId), eq(referenceId), eq(fileStoreId), 
                isNull(), isNull(), isNull())).thenReturn(mockData);
        when(repository.countSheetData(eq(tenantId), eq(referenceId), eq(fileStoreId), 
                isNull())).thenReturn(0);
        when(repository.getSheetWiseCount(eq(tenantId), eq(referenceId), eq(fileStoreId)))
                .thenReturn(mockSheetWiseCounts);

        // When
        SheetDataSearchResponse response = sheetDataService.searchSheetData(request);

        // Then
        assertNotNull(response);
        SheetDataDetails details = response.getSheetDataDetails();
        assertNotNull(details.getSheetWiseCounts());
        assertEquals(1, details.getSheetWiseCounts().size());
        assertEquals("Sheet1", details.getSheetWiseCounts().get(0).get("sheetName"));
    }

    @Test
    void testSearchSheetData_NoCriteriaProvided() {
        // Given
        SheetDataSearchCriteria criteria = SheetDataSearchCriteria.builder()
                .tenantId("pg.citya")
                .build();
                
        SheetDataSearchRequest request = SheetDataSearchRequest.builder()
                .requestInfo(RequestInfo.builder().build())
                .sheetDataSearchCriteria(criteria)
                .build();

        // When
        sheetDataService.searchSheetData(request);

        // Then
        verify(exceptionHandler).throwCustomException(
                eq(ErrorConstants.SHEET_DATA_NO_CRITERIA),
                eq(ErrorConstants.SHEET_DATA_NO_CRITERIA_MESSAGE)
        );
    }

    @Test
    void testDeleteSheetData_Success() {
        // Given
        String tenantId = "pg.citya";
        String referenceId = "ref123";
        String fileStoreId = "file456";
        
        RequestInfo requestInfo = RequestInfo.builder().build();
        SheetDataDeleteRequest request = SheetDataDeleteRequest.builder()
                .requestInfo(requestInfo)
                .tenantId(tenantId)
                .referenceId(referenceId)
                .fileStoreId(fileStoreId)
                .build();

        // When
        sheetDataService.deleteSheetData(request);

        // Then
        verify(producer).push(eq(tenantId), eq("delete-sheet-data-temp"), any(Map.class));
    }

    @Test
    void testDeleteSheetData_VerifyMessageContent() {
        // Given
        String tenantId = "pg.citya";
        String referenceId = "ref123";
        String fileStoreId = "file456";
        
        SheetDataDeleteRequest request = SheetDataDeleteRequest.builder()
                .requestInfo(RequestInfo.builder().build())
                .tenantId(tenantId)
                .referenceId(referenceId)
                .fileStoreId(fileStoreId)
                .build();

        // When
        sheetDataService.deleteSheetData(request);

        // Then
        verify(producer).push(eq(tenantId), eq("delete-sheet-data-temp"), argThat(message -> {
            Map<String, Object> msg = (Map<String, Object>) message;
            return referenceId.equals(msg.get("referenceId")) && 
                   fileStoreId.equals(msg.get("fileStoreId"));
        }));
    }
}