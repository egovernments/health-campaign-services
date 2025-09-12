package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.producer.Producer;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.SheetDataTempRepository;
import org.egov.excelingestion.web.models.SheetDataSearchRequest;
import org.egov.excelingestion.web.models.SheetDataDeleteRequest;
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
    public Map<String, Object> searchSheetData(SheetDataSearchRequest request) {
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

            Map<String, Object> response = new HashMap<>();
            response.put("data", data);
            response.put("totalCount", totalCount);
            if (sheetWiseCounts != null) {
                response.put("sheetWiseCounts", sheetWiseCounts);
            }

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

            // Validate required fields (already validated by annotations, but double-check)
            if (referenceId == null || referenceId.trim().isEmpty() || 
                fileStoreId == null || fileStoreId.trim().isEmpty()) {
                exceptionHandler.throwCustomException(
                    ErrorConstants.SHEET_DATA_DELETE_MISSING_PARAMS,
                    ErrorConstants.SHEET_DATA_DELETE_MISSING_PARAMS_MESSAGE
                );
            }

            // Create delete message
            Map<String, Object> deleteMessage = new HashMap<>();
            deleteMessage.put("referenceId", referenceId);
            deleteMessage.put("fileStoreId", fileStoreId);

            // Push to delete topic
            producer.push(tenantId, "delete-sheet-data-temp", deleteMessage);
            
            log.info("Delete request pushed to topic for referenceId: {}, fileStoreId: {}", 
                    referenceId, fileStoreId);
            
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