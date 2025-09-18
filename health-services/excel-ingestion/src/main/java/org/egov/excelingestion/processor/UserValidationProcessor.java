package org.egov.excelingestion.processor;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.util.LocalizationUtil;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.service.CampaignService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class UserValidationProcessor implements IWorkbookProcessor {

    private final ValidationService validationService;
    private final RestTemplate restTemplate;
    private final ExcelIngestionConfig config;
    private final EnrichmentUtil enrichmentUtil;
    private final CampaignService campaignService;

    public UserValidationProcessor(ValidationService validationService, 
                                 RestTemplate restTemplate, 
                                 ExcelIngestionConfig config,
                                 EnrichmentUtil enrichmentUtil,
                                 CampaignService campaignService) {
        this.validationService = validationService;
        this.restTemplate = restTemplate;
        this.config = config;
        this.enrichmentUtil = enrichmentUtil;
        this.campaignService = campaignService;
    }

    @Override
    public Workbook processWorkbook(Workbook workbook, 
                                  String sheetName,
                                  ProcessResource resource,
                                  RequestInfo requestInfo,
                                  Map<String, String> localizationMap) {
        try {
            // Find the user sheet
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                log.warn("Sheet {} not found in workbook", sheetName);
                return workbook;
            }

            log.info("Starting user validation for sheet: {}", sheetName);

            // Convert sheet data to map list
            List<Map<String, Object>> originalData = convertSheetToMapList(sheet);
            
            if (originalData.isEmpty()) {
                log.info("No data found in sheet, skipping validation");
                return workbook;
            }

            List<ValidationError> errors = new ArrayList<>();
            
            // Add row numbers to data for error reporting
            List<Map<String, Object>> dataWithRowNumbers = addRowNumbers(originalData);
            
            // Validate phone numbers and get list of existing ones in campaign
            Set<String> existingPhonesInCampaign = validatePhoneNumbers(dataWithRowNumbers, resource, requestInfo, errors, localizationMap);
            
            // Validate usernames but skip those with phones already in campaign
            validateUserNames(dataWithRowNumbers, resource.getTenantId(), requestInfo, errors, localizationMap, existingPhonesInCampaign);
            
            log.info("User validation completed with {} errors", errors.size());

            // Only add error columns if there are validation errors
            if (!errors.isEmpty()) {
                log.info("Found {} validation errors, adding error columns", errors.size());
                
                // Check if error columns already exist
                ValidationColumnInfo columnInfo = checkAndAddErrorColumns(sheet, localizationMap);
                
                // Process validation errors - enrich existing or add new error details
                processValidationErrors(sheet, errors, columnInfo, localizationMap);
            } else {
                log.info("No validation errors found, no error columns needed");
            }
            
            // Enrich resource additional details with error information
            enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors);
            
            return workbook;

        } catch (Exception e) {
            log.error("Error processing user validation sheet: {}", e.getMessage(), e);
            return workbook;
        }
    }

    private List<Map<String, Object>> addRowNumbers(List<Map<String, Object>> originalData) {
        List<Map<String, Object>> dataWithRowNumbers = new ArrayList<>();
        
        for (int i = 0; i < originalData.size(); i++) {
            Map<String, Object> rowData = new HashMap<>(originalData.get(i));
            rowData.put("__rowNumber__", i + 3); // Assuming headers at rows 0,1 and data starts at row 2 (1-based)
            dataWithRowNumbers.add(rowData);
        }
        
        return dataWithRowNumbers;
    }

    /**
     * Convert sheet to list of maps
     */
    private List<Map<String, Object>> convertSheetToMapList(Sheet sheet) {
        List<Map<String, Object>> data = new ArrayList<>();
        
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return data;
        }
        
        // Get header names
        List<String> headers = new ArrayList<>();
        for (Cell cell : headerRow) {
            headers.add(getCellValueAsString(cell));
        }
        
        // Process data rows (skip header)
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            Map<String, Object> rowData = new HashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                Cell cell = row.getCell(j);
                String value = getCellValueAsString(cell);
                rowData.put(headers.get(j), value);
            }
            data.add(rowData);
        }
        
        return data;
    }
    
    /**
     * Get cell value as string
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
    
    /**
     * Check if error columns exist and add them if needed
     */
    private ValidationColumnInfo checkAndAddErrorColumns(Sheet sheet, Map<String, String> localizationMap) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return validationService.addValidationColumns(sheet, localizationMap);
        }
        
        // Check if error columns already exist
        boolean hasErrorColumn = false;
        boolean hasStatusColumn = false;
        int errorColumnIndex = -1;
        int statusColumnIndex = -1;
        
        for (Cell cell : headerRow) {
            String headerValue = getCellValueAsString(cell);
            if (ValidationConstants.ERROR_DETAILS_COLUMN_NAME.equals(headerValue)) {
                hasErrorColumn = true;
                errorColumnIndex = cell.getColumnIndex();
            } else if (ValidationConstants.STATUS_COLUMN_NAME.equals(headerValue)) {
                hasStatusColumn = true;
                statusColumnIndex = cell.getColumnIndex();
            }
        }
        
        // If both columns exist, return their positions
        if (hasErrorColumn && hasStatusColumn) {
            ValidationColumnInfo columnInfo = new ValidationColumnInfo();
            columnInfo.setErrorColumnIndex(errorColumnIndex);
            columnInfo.setStatusColumnIndex(statusColumnIndex);
            return columnInfo;
        }
        
        // If columns don't exist, add them
        return validationService.addValidationColumns(sheet, localizationMap);
    }
    
    /**
     * Process validation errors and enrich existing error data with semicolon separation
     */
    private void processValidationErrors(Sheet sheet, List<ValidationError> errors, 
                                       ValidationColumnInfo columnInfo, Map<String, String> localizationMap) {
        // Create a map of row number to errors for quick lookup
        Map<Integer, List<ValidationError>> errorsByRow = new HashMap<>();
        for (ValidationError error : errors) {
            errorsByRow.computeIfAbsent(error.getRowNumber(), k -> new ArrayList<>()).add(error);
        }
        
        // Process each data row (skip header row)
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            int actualRowNumber = i + 1; // Convert to 1-based row number
            List<ValidationError> rowErrors = errorsByRow.get(actualRowNumber);
            
            if (rowErrors != null && !rowErrors.isEmpty()) {
                // Get existing error and status values
                Cell errorCell = row.getCell(columnInfo.getErrorColumnIndex());
                Cell statusCell = row.getCell(columnInfo.getStatusColumnIndex());
                
                String existingErrors = errorCell != null ? getCellValueAsString(errorCell) : "";
                String existingStatus = statusCell != null ? getCellValueAsString(statusCell) : "";
                
                // Build new error message
                StringBuilder errorMessages = new StringBuilder();
                if (existingErrors != null && !existingErrors.trim().isEmpty()) {
                    errorMessages.append(existingErrors);
                }
                
                String status = existingStatus != null && !existingStatus.isEmpty() ? 
                    existingStatus : ValidationConstants.STATUS_VALID;
                
                for (ValidationError error : rowErrors) {
                    if (errorMessages.length() > 0) {
                        errorMessages.append("; ");
                    }
                    errorMessages.append(error.getErrorDetails());
                    
                    // Set status to the most severe error
                    if (ValidationConstants.STATUS_ERROR.equals(error.getStatus())) {
                        status = ValidationConstants.STATUS_ERROR;
                    } else if (ValidationConstants.STATUS_INVALID.equals(error.getStatus()) && 
                              !ValidationConstants.STATUS_ERROR.equals(status)) {
                        status = ValidationConstants.STATUS_INVALID;
                    }
                }
                
                // Create cells if they don't exist
                if (errorCell == null) {
                    errorCell = row.createCell(columnInfo.getErrorColumnIndex());
                }
                if (statusCell == null) {
                    statusCell = row.createCell(columnInfo.getStatusColumnIndex());
                }
                
                // Set the enriched values
                errorCell.setCellValue(errorMessages.toString());
                statusCell.setCellValue(status);
            }
        }
    }


    private Set<String> validatePhoneNumbers(List<Map<String, Object>> sheetData, ProcessResource resource, 
                                    RequestInfo requestInfo, List<ValidationError> errors,
                                    Map<String, String> localizationMap) {
        log.info("Validating phone numbers for {} records", sheetData.size());
        
        // Map phone numbers to their row numbers
        Map<String, Integer> phoneNumberToRowMap = new HashMap<>();
        List<String> allPhoneNumbers = new ArrayList<>();
        
        for (Map<String, Object> rowData : sheetData) {
            String phoneNumber = (String) rowData.get("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER");
            if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
                phoneNumber = phoneNumber.trim();
                phoneNumberToRowMap.put(phoneNumber, (Integer) rowData.get("__rowNumber__"));
                if (!allPhoneNumbers.contains(phoneNumber)) {
                    allPhoneNumbers.add(phoneNumber);
                }
            }
        }
        
        if (allPhoneNumbers.isEmpty()) {
            log.info("No phone numbers to validate");
            return new HashSet<>();
        }
        
        // Check campaign data for existing users with completed status (any campaign)
        String tenantId = resource.getTenantId();
        Set<String> existingInCampaign = new HashSet<>();
        
        try {
            // Search in batches to avoid large array operations
            final int SEARCH_BATCH_SIZE = 500;
            for (int i = 0; i < allPhoneNumbers.size(); i += SEARCH_BATCH_SIZE) {
                int endIndex = Math.min(i + SEARCH_BATCH_SIZE, allPhoneNumbers.size());
                List<String> batch = allPhoneNumbers.subList(i, endIndex);
                
                List<Map<String, Object>> campaignUsers = campaignService.searchCampaignDataByUniqueIdentifiers(
                    batch, "user", "completed", null, tenantId, requestInfo);
                
                for (Map<String, Object> campaignUser : campaignUsers) {
                    String uniqueIdentifier = (String) campaignUser.get("uniqueIdentifier");
                    if (uniqueIdentifier != null) {
                        existingInCampaign.add(uniqueIdentifier);
                        String existingCampaignNumber = (String) campaignUser.get("campaignNumber");
                        log.debug("Phone number {} already exists in campaign {} with completed status", 
                                uniqueIdentifier, existingCampaignNumber);
                    }
                }
            }
            
            if (!existingInCampaign.isEmpty()) {
                log.info("Found {} users already existing in any campaign with completed status - will skip validation for these", 
                        existingInCampaign.size());
            }
        } catch (Exception e) {
            log.error("Error searching campaign data: {}", e.getMessage(), e);
        }
        
        // Filter out phone numbers that already exist in campaign with completed status
        List<String> phoneNumbersToValidate = allPhoneNumbers.stream()
                .filter(phone -> !existingInCampaign.contains(phone))
                .collect(Collectors.toList());
        
        if (phoneNumbersToValidate.isEmpty()) {
            log.info("All phone numbers already exist in campaign with completed status - skipping individual validation");
            return existingInCampaign;
        }
        
        log.info("Validating {} phone numbers (skipped {} already in campaign)", 
                phoneNumbersToValidate.size(), existingInCampaign.size());
        
        // Search in batches of 50 for remaining phone numbers
        final int BATCH_SIZE = 50;
        for (int i = 0; i < phoneNumbersToValidate.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, phoneNumbersToValidate.size());
            List<String> batch = phoneNumbersToValidate.subList(i, endIndex);
            
            try {
                List<Map<String, Object>> existingUsers = searchIndividualsByMobileNumber(batch, tenantId, requestInfo);
                
                for (Map<String, Object> user : existingUsers) {
                    String mobileNumber = (String) user.get("mobileNumber");
                    if (mobileNumber != null && phoneNumberToRowMap.containsKey(mobileNumber)) {
                        ValidationError error = new ValidationError();
                        error.setRowNumber(phoneNumberToRowMap.get(mobileNumber));
                        error.setErrorDetails(LocalizationUtil.getLocalizedMessage(localizationMap, 
                            "HCM_USER_PHONE_NUMBER_EXISTS", 
                            "User with this phone number already exists, and is not suitable for this campaign"));
                        error.setStatus(ValidationConstants.STATUS_INVALID);
                        errors.add(error);
                    }
                }
                
            } catch (Exception e) {
                log.error("Error validating phone number batch: {}", e.getMessage(), e);
                // Add error for all phone numbers in this batch
                for (String phoneNumber : batch) {
                    if (phoneNumberToRowMap.containsKey(phoneNumber)) {
                        ValidationError error = new ValidationError();
                        error.setRowNumber(phoneNumberToRowMap.get(phoneNumber));
                        error.setErrorDetails("Phone number validation failed: " + e.getMessage());
                        error.setStatus(ValidationConstants.STATUS_ERROR);
                        errors.add(error);
                    }
                }
            }
        }
        
        log.info("Phone number validation completed");
        return existingInCampaign;
    }
    
    private void validateUserNames(List<Map<String, Object>> sheetData, String tenantId, 
                                 RequestInfo requestInfo, List<ValidationError> errors,
                                 Map<String, String> localizationMap, Set<String> existingPhonesInCampaign) {
        log.info("Validating usernames for {} records (will skip {} phones already in campaign)", 
                sheetData.size(), existingPhonesInCampaign.size());
        
        // Map usernames to their row numbers  
        Map<String, Integer> userNameToRowMap = new HashMap<>();
        List<String> allUserNames = new ArrayList<>();
        
        for (Map<String, Object> rowData : sheetData) {
            String userName = (String) rowData.get("UserName");
            String phoneNumber = (String) rowData.get("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER");
            String userServiceUuids = (String) rowData.get("UserService Uuids");
            
            // Skip username validation if phone number already exists in campaign with completed status
            if (phoneNumber != null && existingPhonesInCampaign.contains(phoneNumber.trim())) {
                log.debug("Skipping username validation for phone {} - already exists in campaign", phoneNumber.trim());
                continue;
            }
            
            // Only validate username if phone number doesn't already exist and no service UUIDs provided
            if (userName != null && !userName.trim().isEmpty() && 
                phoneNumber != null && !phoneNumber.trim().isEmpty() &&
                (userServiceUuids == null || userServiceUuids.trim().isEmpty())) {
                
                userName = userName.trim();
                userNameToRowMap.put(userName, (Integer) rowData.get("__rowNumber__"));
                if (!allUserNames.contains(userName)) {
                    allUserNames.add(userName);
                }
            }
        }
        
        if (allUserNames.isEmpty()) {
            log.info("No usernames to validate");
            return;
        }
        
        // Search in batches of 50
        final int BATCH_SIZE = 50;
        for (int i = 0; i < allUserNames.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, allUserNames.size());
            List<String> batch = allUserNames.subList(i, endIndex);
            
            try {
                List<Map<String, Object>> existingUsers = searchIndividualsByUsername(batch, tenantId, requestInfo);
                
                for (Map<String, Object> user : existingUsers) {
                    Map<String, Object> userDetails = (Map<String, Object>) user.get("userDetails");
                    if (userDetails != null) {
                        String username = (String) userDetails.get("username");
                        if (username != null && userNameToRowMap.containsKey(username)) {
                            ValidationError error = new ValidationError();
                            error.setRowNumber(userNameToRowMap.get(username));
                            error.setErrorDetails(LocalizationUtil.getLocalizedMessage(localizationMap, 
                                "HCM_USER_USERNAME_EXISTS", 
                                "User with this username already exists"));
                            error.setStatus(ValidationConstants.STATUS_INVALID);
                            errors.add(error);
                        }
                    }
                }
                
            } catch (Exception e) {
                log.error("Error validating username batch: {}", e.getMessage(), e);
                // Add error for all usernames in this batch
                for (String userName : batch) {
                    if (userNameToRowMap.containsKey(userName)) {
                        ValidationError error = new ValidationError();
                        error.setRowNumber(userNameToRowMap.get(userName));
                        error.setErrorDetails("Username validation failed: " + e.getMessage());
                        error.setStatus(ValidationConstants.STATUS_ERROR);
                        errors.add(error);
                    }
                }
            }
        }
        
        log.info("Username validation completed");
    }
    
    private List<Map<String, Object>> searchIndividualsByMobileNumber(List<String> mobileNumbers, 
                                                                     String tenantId, 
                                                                     RequestInfo requestInfo) throws Exception {
        String url = config.getHealthIndividualHost() + config.getHealthIndividualSearchPath();
        
        Map<String, Object> searchBody = new HashMap<>();
        searchBody.put("RequestInfo", requestInfo);
        
        Map<String, Object> individual = new HashMap<>();
        individual.put("mobileNumber", mobileNumbers);
        searchBody.put("Individual", individual);
        
        Map<String, String> params = new HashMap<>();
        params.put("limit", "55");
        params.put("offset", "0");
        params.put("tenantId", tenantId);
        params.put("includeDeleted", "true");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(searchBody, headers);
        
        // Build URL with parameters
        StringBuilder urlWithParams = new StringBuilder(url).append("?");
        params.forEach((key, value) -> urlWithParams.append(key).append("=").append(value).append("&"));
        
        log.info("Searching individuals by mobile number: {}", mobileNumbers);
        ResponseEntity<Map> response = restTemplate.exchange(
            urlWithParams.toString(), 
            HttpMethod.POST, 
            entity, 
            Map.class
        );
        
        if (response.getBody() != null && response.getBody().get("Individual") != null) {
            return (List<Map<String, Object>>) response.getBody().get("Individual");
        }
        
        return new ArrayList<>();
    }
    
    private List<Map<String, Object>> searchIndividualsByUsername(List<String> usernames, 
                                                                String tenantId, 
                                                                RequestInfo requestInfo) throws Exception {
        String url = config.getHealthIndividualHost() + config.getHealthIndividualSearchPath();
        
        Map<String, Object> searchBody = new HashMap<>();
        searchBody.put("RequestInfo", requestInfo);
        
        Map<String, Object> individual = new HashMap<>();
        individual.put("username", usernames);
        searchBody.put("Individual", individual);
        
        Map<String, String> params = new HashMap<>();
        params.put("tenantId", tenantId);
        params.put("limit", "51");
        params.put("offset", "0");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(searchBody, headers);
        
        // Build URL with parameters
        StringBuilder urlWithParams = new StringBuilder(url).append("?");
        params.forEach((key, value) -> urlWithParams.append(key).append("=").append(value).append("&"));
        
        log.info("Searching individuals by username: {}", usernames);
        ResponseEntity<Map> response = restTemplate.exchange(
            urlWithParams.toString(), 
            HttpMethod.POST, 
            entity, 
            Map.class
        );
        
        if (response.getBody() != null && response.getBody().get("Individual") != null) {
            return (List<Map<String, Object>>) response.getBody().get("Individual");
        }
        
        return new ArrayList<>();
    }
}