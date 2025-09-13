package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.producer.Producer;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.SheetDataTempRepository;
import org.egov.excelingestion.web.models.SheetDataSearchRequest;
import org.egov.excelingestion.web.models.SheetDataDeleteRequest;
import org.egov.excelingestion.web.models.SheetDataSearchResponse;
import org.egov.excelingestion.web.models.SheetDataDetails;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for sheet data temp operations
 */
@Service
@Slf4j
public class SheetDataService {

    private final SheetDataTempRepository repository;
    private final Producer producer;
    private final CustomExceptionHandler exceptionHandler;

    public SheetDataService(SheetDataTempRepository repository, 
                          Producer producer,
                          CustomExceptionHandler exceptionHandler) {
        this.repository = repository;
        this.producer = producer;
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Search sheet data based on criteria
     */
    public SheetDataSearchResponse searchSheetData(SheetDataSearchRequest request) {
        try {
            String tenantId = request.getSheetDataSearchCriteria().getTenantId();
            String referenceId = request.getSheetDataSearchCriteria().getReferenceId();
            String fileStoreId = request.getSheetDataSearchCriteria().getFileStoreId();
            String sheetName = request.getSheetDataSearchCriteria().getSheetName();
            Integer limit = request.getSheetDataSearchCriteria().getLimit();
            Integer offset = request.getSheetDataSearchCriteria().getOffset();
            
            // Validate at least one search criteria is provided
            if (referenceId == null && fileStoreId == null && sheetName == null) {
                exceptionHandler.throwCustomException(
                    ErrorConstants.SHEET_DATA_NO_CRITERIA,
                    ErrorConstants.SHEET_DATA_NO_CRITERIA_MESSAGE
                );
            }

            // Get data
            List<Map<String, Object>> data = repository.searchSheetData(tenantId, referenceId, 
                    fileStoreId, sheetName, limit, offset);
            
            // Get total count
            Integer totalCount = repository.countSheetData(tenantId, referenceId, fileStoreId, sheetName);
            
            // Get sheet-wise count if searching by reference and filestore
            List<Map<String, Object>> sheetWiseCounts = null;
            if (referenceId != null && fileStoreId != null && sheetName == null) {
                sheetWiseCounts = repository.getSheetWiseCount(tenantId, referenceId, fileStoreId);
            }

            // Create response info
            ResponseInfo responseInfo = ResponseInfo.builder()
                    .apiId(request.getRequestInfo().getApiId())
                    .ver(request.getRequestInfo().getVer())
                    .ts(request.getRequestInfo().getTs())
                    .status("successful")
                    .build();

            // Create sheet data details
            SheetDataDetails sheetDataDetails = SheetDataDetails.builder()
                    .data(data)
                    .totalCount(totalCount)
                    .sheetWiseCounts(sheetWiseCounts)
                    .build();

            // Create final response
            SheetDataSearchResponse response = SheetDataSearchResponse.builder()
                    .responseInfo(responseInfo)
                    .sheetDataDetails(sheetDataDetails)
                    .build();

            log.info("Sheet data search successful. Found {} records out of {} total", 
                    data.size(), totalCount);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error searching sheet data: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(
                ErrorConstants.SHEET_DATA_SEARCH_ERROR,
                ErrorConstants.SHEET_DATA_SEARCH_ERROR_MESSAGE.replace("{0}", e.getMessage()),
                e
            );
            return null; // Never reached
        }
    }

    /**
     * Delete sheet data by pushing to delete topic
     */
    public void deleteSheetData(SheetDataDeleteRequest request) {
        try {
            String tenantId = request.getTenantId();
            String referenceId = request.getReferenceId();
            String fileStoreId = request.getFileStoreId();

            // Create delete message
            Map<String, Object> deleteMessage = new HashMap<>();
            deleteMessage.put("tenantId", tenantId);
            deleteMessage.put("referenceId", referenceId);
            deleteMessage.put("fileStoreId", fileStoreId);

            // Push to delete topic
            producer.push(tenantId, "delete-sheet-data-temp", deleteMessage);
            
            log.info("Delete request pushed to topic for tenantId: {}, referenceId: {}, fileStoreId: {}", 
                    tenantId, referenceId, fileStoreId);
            
        } catch (Exception e) {
            log.error("Error deleting sheet data: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(
                ErrorConstants.SHEET_DATA_DELETE_ERROR,
                ErrorConstants.SHEET_DATA_DELETE_ERROR_MESSAGE.replace("{0}", e.getMessage()),
                e
            );
        }
    }
}