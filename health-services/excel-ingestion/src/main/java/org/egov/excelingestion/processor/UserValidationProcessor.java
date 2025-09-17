package org.egov.excelingestion.processor;

import lombok.extern.slf4j.Slf4j;

import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.web.models.*;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.egov.excelingestion.util.LocalizationUtil;
import org.egov.excelingestion.util.EnrichmentUtil;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.util.ErrorColumnUtil;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.service.CampaignService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.excelingestion.config.ProcessingConstants;
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
public class UserValidationProcessor implements ISheetDataProcessor {

    private final ValidationService validationService;
    private final RestTemplate restTemplate;
    private final ExcelIngestionConfig config;
    private final EnrichmentUtil enrichmentUtil;
    private final MDMSService mdmsService;
    private final SchemaColumnDefUtil schemaColumnDefUtil;
    private final ErrorColumnUtil errorColumnUtil;
    private final CampaignService campaignService;

    public UserValidationProcessor(ValidationService validationService, 
                                 RestTemplate restTemplate, 
                                 ExcelIngestionConfig config,
                                 EnrichmentUtil enrichmentUtil,
                                 MDMSService mdmsService,
                                 SchemaColumnDefUtil schemaColumnDefUtil,
                                 ErrorColumnUtil errorColumnUtil,
                                 CampaignService campaignService) {
        this.validationService = validationService;
        this.restTemplate = restTemplate;
        this.config = config;
        this.enrichmentUtil = enrichmentUtil;
        this.mdmsService = mdmsService;
        this.schemaColumnDefUtil = schemaColumnDefUtil;
        this.errorColumnUtil = errorColumnUtil;
        this.campaignService = campaignService;
    }

    @Override
    public SheetGenerationResult processSheetData(List<Map<String, Object>> originalData,
                                                ProcessResource resource,
                                                RequestInfo requestInfo,
                                                Map<String, String> localizationMap) {
        log.info("Starting user validation for {} records", originalData.size());
        
        List<ValidationError> errors = new ArrayList<>();
        List<Map<String, Object>> processedData = new ArrayList<>();
        
        try {
            // Add row numbers to data for error reporting
            List<Map<String, Object>> dataWithRowNumbers = addRowNumbers(originalData);
            
            // Validate phone numbers and get list of existing ones in campaign
            Set<String> existingPhonesInCampaign = validatePhoneNumbers(dataWithRowNumbers, resource, requestInfo, errors, localizationMap);
            
            // Validate usernames but skip those with phones already in campaign
            validateUserNames(dataWithRowNumbers, resource.getTenantId(), requestInfo, errors, localizationMap, existingPhonesInCampaign);
            
            // Process data and add error information
            processedData = addErrorInformationToData(dataWithRowNumbers, errors, localizationMap);
            
            log.info("User validation completed with {} errors", errors.size());
            
        } catch (Exception e) {
            log.error("Error during user validation: {}", e.getMessage(), e);
            // Create general error for all rows
            for (int i = 0; i < originalData.size(); i++) {
                ValidationError error = new ValidationError();
                error.setRowNumber(i + 3); // Assuming headers at rows 0,1 and data starts at row 2
                error.setErrorDetails("User validation failed: " + e.getMessage());
                error.setStatus(ValidationConstants.STATUS_ERROR);
                errors.add(error);
            }
            processedData = addErrorInformationToData(addRowNumbers(originalData), errors, localizationMap);
        }
        
        // Enrich resource additional details with error information
        enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors);
        
        // Generate column definitions from schema
        List<ColumnDef> columnDefs = generateColumnDefinitionsFromSchema(resource, requestInfo, localizationMap);
        
        return SheetGenerationResult.builder()
                .columnDefs(columnDefs)
                .data(processedData)
                .build();
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

    private List<Map<String, Object>> addErrorInformationToData(List<Map<String, Object>> dataWithRowNumbers,
                                                               List<ValidationError> errors,
                                                               Map<String, String> localizationMap) {
        List<Map<String, Object>> processedData = new ArrayList<>();
        
        // Create a map of row number to errors for quick lookup
        Map<Integer, List<ValidationError>> errorsByRow = new HashMap<>();
        for (ValidationError error : errors) {
            errorsByRow.computeIfAbsent(error.getRowNumber(), k -> new ArrayList<>()).add(error);
        }
        
        for (Map<String, Object> rowData : dataWithRowNumbers) {
            Map<String, Object> processedRow = new HashMap<>(rowData);
            Integer rowNumber = (Integer) rowData.get("__rowNumber__");
            
            // Remove internal row number field
            processedRow.remove("__rowNumber__");
            
            // Check for existing error information
            String existingErrors = (String) processedRow.get(ValidationConstants.ERROR_DETAILS_COLUMN_NAME);
            String existingStatus = (String) processedRow.get(ValidationConstants.STATUS_COLUMN_NAME);
            
            // Add error information if there are errors for this row
            if (errorsByRow.containsKey(rowNumber)) {
                List<ValidationError> rowErrors = errorsByRow.get(rowNumber);
                StringBuilder errorMessages = new StringBuilder();
                
                // Start with existing errors if any
                if (existingErrors != null && !existingErrors.trim().isEmpty()) {
                    errorMessages.append(existingErrors);
                }
                
                String status = existingStatus != null ? existingStatus : ValidationConstants.STATUS_VALID;
                
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
                
                processedRow.put(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, errorMessages.toString());
                processedRow.put(ValidationConstants.STATUS_COLUMN_NAME, status);
            } else {
                // If no new errors but no existing data, set defaults
                if (existingErrors == null) {
                    processedRow.put(ValidationConstants.ERROR_DETAILS_COLUMN_NAME, "");
                }
                if (existingStatus == null) {
                    processedRow.put(ValidationConstants.STATUS_COLUMN_NAME, ""); // Leave blank instead of "valid"
                }
            }
            
            processedData.add(processedRow);
        }
        
        return processedData;
    }

    private List<ColumnDef> generateColumnDefinitionsFromSchema(ProcessResource resource,
                                                               RequestInfo requestInfo,
                                                               Map<String, String> localizationMap) {
        List<ColumnDef> columnDefs = new ArrayList<>();
        
        try {
            // Fetch user schema from MDMS
            Map<String, Object> filters = new HashMap<>();
            filters.put("title", "user-microplan-ingestion");
            
            List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                    requestInfo, resource.getTenantId(), ProcessingConstants.MDMS_SCHEMA_CODE, filters, 1, 0);
            
            if (!mdmsList.isEmpty()) {
                Map<String, Object> mdmsData = mdmsList.get(0);
                Map<String, Object> data = (Map<String, Object>) mdmsData.get("data");
                Map<String, Object> properties = (Map<String, Object>) data.get("properties");
                
                if (properties != null) {
                    ObjectMapper mapper = new ObjectMapper();
                    String schemaJson = mapper.writeValueAsString(properties);
                    // Use the shared utility to convert schema to column defs
                    columnDefs = schemaColumnDefUtil.convertSchemaToColumnDefs(schemaJson);
                    
                    // Set technical names from the name field if not already set
                    for (ColumnDef columnDef : columnDefs) {
                        if (columnDef.getTechnicalName() == null || columnDef.getTechnicalName().isEmpty()) {
                            columnDef.setTechnicalName(columnDef.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching schema-based column definitions: {}", e.getMessage(), e);
            // Fall back to basic column definitions if schema fetch fails
        }
        
        // Add error columns using the ErrorColumnUtil for consistent styling
        List<ColumnDef> errorColumns = errorColumnUtil.createErrorColumnDefs(localizationMap);
        columnDefs.addAll(errorColumns);
        
        return columnDefs;
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
        
        // Get campaign details to fetch campaign number
        String campaignId = resource.getReferenceId();
        String tenantId = resource.getTenantId();
        String campaignNumber = null;
        
        try {
            var campaignDetail = campaignService.searchCampaignById(campaignId, tenantId, requestInfo);
            if (campaignDetail != null) {
                campaignNumber = campaignDetail.getCampaignNumber();
                log.info("Found campaign number: {} for campaign ID: {}", campaignNumber, campaignId);
            }
        } catch (Exception e) {
            log.error("Error fetching campaign details for campaign ID: {}", campaignId, e);
        }
        
        // First, check campaign data for existing users with completed status
        Set<String> existingInCampaign = new HashSet<>();
        if (campaignNumber != null) {
            try {
                List<Map<String, Object>> campaignUsers = campaignService.searchCampaignDataByPhoneNumbers(
                    allPhoneNumbers, campaignNumber, tenantId, requestInfo);
                
                for (Map<String, Object> campaignUser : campaignUsers) {
                    String uniqueIdentifier = (String) campaignUser.get("uniqueIdentifier");
                    if (uniqueIdentifier != null) {
                        existingInCampaign.add(uniqueIdentifier);
                        log.debug("Phone number {} already exists in campaign {} with completed status", 
                                uniqueIdentifier, campaignNumber);
                    }
                }
                
                if (!existingInCampaign.isEmpty()) {
                    log.info("Found {} users already existing in campaign {} with completed status - will skip validation for these", 
                            existingInCampaign.size(), campaignNumber);
                }
            } catch (Exception e) {
                log.error("Error searching campaign data: {}", e.getMessage(), e);
            }
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