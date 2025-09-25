package org.egov.excelingestion.processor;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.util.LocalizationUtil;
import org.egov.excelingestion.util.BoundaryUtil;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.util.ExcelUtil;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.BoundaryService;
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
    private final BoundaryService boundaryService;
    private final BoundaryUtil boundaryUtil;
    private final ExcelUtil excelUtil;
    private final CustomExceptionHandler exceptionHandler;

    public UserValidationProcessor(ValidationService validationService, 
                                 RestTemplate restTemplate, 
                                 ExcelIngestionConfig config,
                                 EnrichmentUtil enrichmentUtil,
                                 CampaignService campaignService,
                                 BoundaryService boundaryService,
                                 BoundaryUtil boundaryUtil,
                                 ExcelUtil excelUtil,
                                 CustomExceptionHandler exceptionHandler) {
        this.validationService = validationService;
        this.restTemplate = restTemplate;
        this.config = config;
        this.enrichmentUtil = enrichmentUtil;
        this.campaignService = campaignService;
        this.boundaryService = boundaryService;
        this.boundaryUtil = boundaryUtil;
        this.excelUtil = excelUtil;
        this.exceptionHandler = exceptionHandler;
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

            // Convert sheet data to map list - CACHED VERSION
            List<Map<String, Object>> sheetData = excelUtil.convertSheetToMapListCached(
                    resource.getFileStoreId(), sheetName, sheet);

            List<ValidationError> errors = new ArrayList<>();

            // Validate that at least one active user exists
            validateAtLeastOneActiveUser(sheetData, errors, localizationMap);
            
            // Validate phone numbers and get list of existing ones in campaign
            Set<String> existingPhonesInCampaign = validatePhoneNumbers(sheetData, resource, requestInfo, errors, localizationMap);
            
            // Validate usernames but skip those with phones already in campaign
            validateUserNames(sheetData, resource.getTenantId(), requestInfo, errors, localizationMap, existingPhonesInCampaign);
            
            // Validate boundary keys for active users
            validateBoundaryKeys(sheetData, errors, localizationMap);
            
            // Validate boundary codes against campaign boundaries
            validateCampaignBoundaries(sheetData, resource, requestInfo, errors, localizationMap);
            
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
            exceptionHandler.throwCustomException(ErrorConstants.USER_VALIDATION_FAILED, 
                ErrorConstants.USER_VALIDATION_FAILED_MESSAGE + ": " + e.getMessage(), e);
            return workbook; // never reached
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
            String headerValue = ExcelUtil.getCellValueAsString(cell);
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
        for (int i = 1; i <= ExcelUtil.findActualLastRowWithData(sheet); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            int actualRowNumber = i + 1; // Convert to 1-based row number
            List<ValidationError> rowErrors = errorsByRow.get(actualRowNumber);
            
            if (rowErrors != null && !rowErrors.isEmpty()) {
                // Get existing error and status values
                Cell errorCell = row.getCell(columnInfo.getErrorColumnIndex());
                Cell statusCell = row.getCell(columnInfo.getStatusColumnIndex());
                
                String existingErrors = errorCell != null ? ExcelUtil.getCellValueAsString(errorCell) : "";
                String existingStatus = statusCell != null ? ExcelUtil.getCellValueAsString(statusCell) : "";
                
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
            String phoneNumber = ExcelUtil.getValueAsString(rowData.get("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"));
            if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
                phoneNumber = phoneNumber.trim();
                phoneNumberToRowMap.put(phoneNumber, (Integer) rowData.get("__actualRowNumber__"));
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
            exceptionHandler.throwCustomException( 
                    ErrorConstants.CAMPAIGN_DATA_SEARCH_ERROR, 
                    ErrorConstants.CAMPAIGN_DATA_SEARCH_ERROR_MESSAGE, e);
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
                exceptionHandler.throwCustomException(ErrorConstants.PHONE_VALIDATION_FAILED, 
                    ErrorConstants.PHONE_VALIDATION_FAILED_MESSAGE, e);
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
            String userName = ExcelUtil.getValueAsString(rowData.get("UserName"));
            String phoneNumber = ExcelUtil.getValueAsString(rowData.get("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"));
            String userServiceUuids = ExcelUtil.getValueAsString(rowData.get("UserService Uuids"));
            
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
                userNameToRowMap.put(userName, (Integer) rowData.get("__actualRowNumber__"));
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
                exceptionHandler.throwCustomException(ErrorConstants.USERNAME_VALIDATION_FAILED, 
                    ErrorConstants.USERNAME_VALIDATION_FAILED_MESSAGE, e);
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
    
    /**
     * Validate boundary keys for active users
     */
    private void validateBoundaryKeys(List<Map<String, Object>> sheetData, List<ValidationError> errors,
                                    Map<String, String> localizationMap) {
        log.info("Validating boundary keys for {} records", sheetData.size());
        
        for (Map<String, Object> rowData : sheetData) {
            String usage = ExcelUtil.getValueAsString(rowData.get("HCM_ADMIN_CONSOLE_USER_USAGE"));
            String boundaryCode = ExcelUtil.getValueAsString(rowData.get("HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
            Integer rowNumber = (Integer) rowData.get("__actualRowNumber__");
            
            // Only validate boundary key if usage is "active" 
            if ("Active".equals(usage)) {
                if (boundaryCode == null || boundaryCode.trim().isEmpty()) {
                    ValidationError error = new ValidationError();
                    error.setRowNumber(rowNumber);
                    error.setErrorDetails(LocalizationUtil.getLocalizedMessage(localizationMap, 
                        "HCM_USER_BOUNDARY_CODE_REQUIRED_FOR_ACTIVE_USAGE", 
                        "Boundary selection is required if usage is active"));
                    error.setStatus(ValidationConstants.STATUS_INVALID);
                    errors.add(error);
                }
            }
        }
        
        log.info("Boundary key validation completed");
    }

    /**
     * Validate boundary codes against campaign boundaries
     * Get campaign boundaries, enrich them with children, and check if all HCM_ADMIN_CONSOLE_BOUNDARY_CODE values exist
     */
    private void validateCampaignBoundaries(List<Map<String, Object>> sheetData, ProcessResource resource,
                                          RequestInfo requestInfo, List<ValidationError> errors,
                                          Map<String, String> localizationMap) {
        log.info("Validating boundary codes against campaign boundaries for {} records", sheetData.size());
        
        try {
            // Get campaign boundaries
            String referenceId = resource.getReferenceId(); // This should be campaignId
            java.util.List<CampaignSearchResponse.BoundaryDetail> campaignBoundaries = 
                    campaignService.getBoundariesFromCampaign(referenceId, resource.getTenantId(), requestInfo);
            
            if (campaignBoundaries == null || campaignBoundaries.isEmpty()) {
                log.warn("No boundaries found for campaign ID: {}, skipping boundary validation", referenceId);
                return;
            }
            
            // Get hierarchy type from resource (should be available from generation context)
            String hierarchyType = resource.getHierarchyType();
            if (hierarchyType == null) {
                log.warn("No hierarchy type available, skipping boundary validation");
                return;
            }
            
            // Fetch boundary relationships with includeChildren=true to get all boundary codes
            BoundarySearchResponse boundaryResponse = boundaryService.fetchBoundaryRelationship(
                    resource.getTenantId(), hierarchyType, requestInfo);
            
            if (boundaryResponse == null) {
                log.warn("Could not fetch boundary relationships, skipping boundary validation");
                return;
            }
            
            // Use BoundaryUtil to get enriched boundary codes with caching
            Set<String> validBoundaryCodes = boundaryUtil.getEnrichedBoundaryCodesFromCampaign(
                    resource.getId(), referenceId, resource.getTenantId(), resource.getHierarchyType(), requestInfo);
            
            log.info("Found {} valid boundary codes for campaign from {} configured boundaries", 
                    validBoundaryCodes.size(), campaignBoundaries.size());
            
            // Validate each row's boundary code
            for (Map<String, Object> rowData : sheetData) {
                String boundaryCode = ExcelUtil.getValueAsString(rowData.get("HCM_ADMIN_CONSOLE_BOUNDARY_CODE"));
                Integer rowNumber = (Integer) rowData.get("__actualRowNumber__");
                
                if (boundaryCode != null && !boundaryCode.trim().isEmpty()) {
                    boundaryCode = boundaryCode.trim();
                    
                    if (!validBoundaryCodes.contains(boundaryCode)) {
                        ValidationError error = new ValidationError();
                        error.setRowNumber(rowNumber);
                        error.setErrorDetails(LocalizationUtil.getLocalizedMessage(localizationMap, 
                            "HCM_BOUNDARY_CODE_NOT_IN_CAMPAIGN", 
                            "This boundary does not exist in the campaign's boundary"));
                        error.setStatus(ValidationConstants.STATUS_INVALID);
                        errors.add(error);
                        
                        log.debug("Invalid boundary code {} at row {} - not in campaign boundaries", 
                                boundaryCode, rowNumber);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Error validating campaign boundaries: {}", e.getMessage(), e);
            // Don't fail the entire process for boundary validation errors
        }
        
        log.info("Campaign boundary validation completed");
    }

    /**
     * Validate that at least one active user exists in the sheet
     */
    private void validateAtLeastOneActiveUser(List<Map<String, Object>> sheetData, 
                                            List<ValidationError> errors, 
                                            Map<String, String> localizationMap) {
        try {
            // Count active users
            long activeUserCount = sheetData.stream()
                .filter(rowData -> {
                    String usage = ExcelUtil.getValueAsString(rowData.get("HCM_ADMIN_CONSOLE_USER_USAGE"));
                    return "Active".equals(usage);
                })
                .count();
            
            if (activeUserCount == 0) {
                String errorMessage = localizationMap.getOrDefault(
                    "HCM_USER_ATLEAST_ONE_ACTIVE_REQUIRED", 
                    "At least one active user is required in the sheet.");
                
                ValidationError error = new ValidationError();
                error.setRowNumber(3); // First data row after headers (1-indexed: 1=hidden, 2=visible header, 3=first data)
                error.setColumnName("HCM_ADMIN_CONSOLE_USER_USAGE");
                error.setStatus(ValidationConstants.STATUS_INVALID);
                error.setErrorDetails(errorMessage);
                errors.add(error);
                
                log.info("No active users found in sheet, added validation error");
            } else {
                log.info("Found {} active users in sheet", activeUserCount);
            }
            
        } catch (Exception e) {
            log.error("Error validating active user count: {}", e.getMessage(), e);
            // Don't add errors for technical failures - just log and continue
        }
    }
}