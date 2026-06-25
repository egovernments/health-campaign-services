package org.egov.excelingestion.processor;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.config.ValidationConstants;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.service.ValidationService;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.web.models.*;
import org.egov.common.contract.request.RequestInfo;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

            // Server-side guard: boundary SELECTION names must exist in the campaign hierarchy. The Excel
            // dropdown is client-side only and bypassable, so an off-dropdown value (e.g. a typed "Province 7")
            // is re-checked here and fails the upload.
            if (config.isValidateBoundarySelectionNames()) {
                boundaryUtil.validateBoundarySelectionNames(sheetData, resource.getTenantId(),
                        resource.getHierarchyType(), requestInfo, localizationMap, errors);
            }

            // Validate worker IDs against worker registry
            validateWorkerIds(sheetData, resource.getTenantId(), requestInfo, errors, localizationMap);

            // Validate beneficiary code, bank account and bank code have no whitespace
            validateBeneficiaryCode(sheetData, errors, localizationMap);
            validateFieldNoWhitespace(sheetData, errors, localizationMap,
                    ProcessingConstants.BANK_ACCOUNT_COL,
                    ValidationConstants.LOC_BANK_ACCOUNT_WHITESPACE,
                    ValidationConstants.DEFAULT_BANK_ACCOUNT_WHITESPACE);
            validateFieldNoWhitespace(sheetData, errors, localizationMap,
                    ProcessingConstants.BANK_CODE_COL,
                    ValidationConstants.LOC_BANK_CODE_WHITESPACE,
                    ValidationConstants.DEFAULT_BANK_CODE_WHITESPACE);

            log.info("User validation completed with {} errors", errors.size());
            enrichmentUtil.logValidationErrors(resource.getReferenceId(), sheetName, errors);

            // Tag every row in the cached sheetData with its per-row validity so the
            // persistence step (saveSheetDataToTemp) writes it into rowJson, allowing
            // downstream consumers (project-factory) to skip invalid rows without
            // re-running validation.
            enrichSheetDataWithRowStatus(sheetData, errors);

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
            enrichmentUtil.enrichErrorAndStatusInAdditionalDetails(resource, errors, ValidationConstants.SHEET_KIND_USER);
            
            return workbook;

        } catch (Exception e) {
            log.error("Error processing user validation sheet: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.USER_VALIDATION_FAILED, 
                ErrorConstants.USER_VALIDATION_FAILED_MESSAGE + ": " + e.getMessage(), e);
            return workbook; // never reached
        }
    }

    /**
     * Tag each row in the cached sheetData with #status# and #errorDetails#.
     * sheetData is the cached list reference returned by convertSheetToMapListCached;
     * mutations propagate to the persistence path that re-fetches the same cached list.
     * Errors are matched to rows via the existing __actualRowNumber__ marker.
     */
    private void enrichSheetDataWithRowStatus(List<Map<String, Object>> sheetData,
                                              List<ValidationError> errors) {
        Map<Integer, List<String>> errorsByRow = new HashMap<>();
        for (ValidationError err : errors) {
            Integer rowNum = err.getRowNumber();
            if (rowNum == null) continue;
            String detail = err.getErrorDetails();
            if (detail == null || detail.isEmpty()) continue;
            errorsByRow.computeIfAbsent(rowNum, k -> new ArrayList<>()).add(detail);
        }

        for (Map<String, Object> rowData : sheetData) {
            Integer rowNumber = (Integer) rowData.get("__actualRowNumber__");
            List<String> rowErrors = rowNumber == null ? null : errorsByRow.get(rowNumber);
            if (rowErrors != null && !rowErrors.isEmpty()) {
                rowData.put(ValidationConstants.ROW_JSON_STATUS_KEY, ValidationConstants.ROW_STATUS_INVALID);
                Set<String> dedup = new LinkedHashSet<>(rowErrors);
                rowData.put(ValidationConstants.ROW_JSON_ERROR_DETAILS_KEY, String.join("; ", dedup));
            } else {
                // Only set VALID if not already invalid from a prior pass
                Object existing = rowData.get(ValidationConstants.ROW_JSON_STATUS_KEY);
                if (!ValidationConstants.ROW_STATUS_INVALID.equals(existing)) {
                    rowData.put(ValidationConstants.ROW_JSON_STATUS_KEY, ValidationConstants.ROW_STATUS_VALID);
                }
            }
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

        // Build localized header name → column index map (single pass, O(n))
        Map<String, Integer> headerNameToColIndex = new HashMap<>();
        Row headerRow = sheet.getRow(0);
        if (headerRow != null) {
            for (Cell cell : headerRow) {
                String val = ExcelUtil.getCellValueAsString(cell);
                if (val != null && !val.isEmpty()) {
                    headerNameToColIndex.put(val, cell.getColumnIndex());
                }
            }
        }

        // Create cell error highlight style once (reuse across all rows)
        CellStyle errorHighlightStyle = sheet.getWorkbook().createCellStyle();
        errorHighlightStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        errorHighlightStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // Find the maximum error row number
        int maxErrorRowNumber = errors.stream()
            .filter(e -> e.getRowNumber() != null)
            .mapToInt(ValidationError::getRowNumber)
            .max()
            .orElse(0);

        // Process each data row (skip header row) - loop up to max of actual data rows or max error row
        int maxRowToProcess = Math.max(ExcelUtil.findActualLastRowWithData(sheet), maxErrorRowNumber - 1);
        for (int i = 1; i <= maxRowToProcess; i++) {
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

                // Use a Set to store unique error messages
                Set<String> uniqueErrorMessages = new LinkedHashSet<>();
                if (existingErrors != null && !existingErrors.trim().isEmpty()) {
                    uniqueErrorMessages.addAll(Arrays.asList(existingErrors.split("\s*;\s*")));
                }

                String status = existingStatus != null && !existingStatus.isEmpty() ?
                    existingStatus : ValidationConstants.STATUS_VALID;

                for (ValidationError error : rowErrors) {
                    uniqueErrorMessages.add(error.getErrorDetails());

                    // Set status to the most severe error
                    if (ValidationConstants.STATUS_ERROR.equals(error.getStatus())) {
                        status = ValidationConstants.STATUS_ERROR;
                    } else if (ValidationConstants.STATUS_INVALID.equals(error.getStatus()) &&
                              !ValidationConstants.STATUS_ERROR.equals(status)) {
                        status = ValidationConstants.STATUS_INVALID;
                    }

                    // Highlight specific column cell if columnName is set on the error
                    if (error.getColumnName() != null) {
                        String localizedColName = LocalizationUtil.getLocalizedMessage(
                                localizationMap, error.getColumnName(), error.getColumnName());
                        Integer colIdx = headerNameToColIndex.get(localizedColName);
                        if (colIdx != null) {
                            Cell targetCell = row.getCell(colIdx);
                            if (targetCell == null) {
                                targetCell = row.createCell(colIdx);
                            }
                            targetCell.setCellStyle(errorHighlightStyle);
                        }
                    }
                }
                String finalErrorMessage = String.join("; ", uniqueErrorMessages);

                // Create cells if they don't exist
                if (errorCell == null) {
                    errorCell = row.createCell(columnInfo.getErrorColumnIndex());
                }
                if (statusCell == null) {
                    statusCell = row.createCell(columnInfo.getStatusColumnIndex());
                }

                // Set the enriched values
                errorCell.setCellValue(finalErrorMessage);
                statusCell.setCellValue(status);
            }
        }
    }


    private Set<String> validatePhoneNumbers(List<Map<String, Object>> sheetData, ProcessResource resource, 
                                    RequestInfo requestInfo, List<ValidationError> errors,
                                    Map<String, String> localizationMap) {
        log.info("Validating phone numbers for {} records", sheetData.size());
        
        // Map phone numbers to their row numbers.
        // Use a LinkedHashSet for O(1) dedup (preserving insertion order) instead of
        // List.contains() in a loop, which is O(n^2) at large row counts.
        Map<String, Integer> phoneNumberToRowMap = new HashMap<>();
        Set<String> uniquePhoneNumbers = new LinkedHashSet<>();

        for (Map<String, Object> rowData : sheetData) {
            String phoneNumber = ExcelUtil.getValueAsString(rowData.get("HCM_ADMIN_CONSOLE_USER_PHONE_NUMBER"));
            if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
                phoneNumber = phoneNumber.trim();
                phoneNumberToRowMap.put(phoneNumber, (Integer) rowData.get("__actualRowNumber__"));
                uniquePhoneNumbers.add(phoneNumber);
            }
        }

        if (uniquePhoneNumbers.isEmpty()) {
            log.info("No phone numbers to validate");
            return new HashSet<>();
        }

        // Materialize as a List for the index-based batch (subList) and stream operations below.
        List<String> allPhoneNumbers = new ArrayList<>(uniquePhoneNumbers);
        
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
        
        // Search in bounded-parallel batches (size configurable; guard 0 from mocks/unset -> 50)
        final int BATCH_SIZE = config.getUserSearchBatchSize() > 0 ? config.getUserSearchBatchSize() : 50;
        List<Map<String, Object>> existingUsers;
        try {
            existingUsers = parallelBatchedSearch(phoneNumbersToValidate, BATCH_SIZE,
                    batch -> searchIndividualsByMobileNumber(batch, tenantId, requestInfo));
        } catch (Exception e) {
            log.error("Error validating phone numbers: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.PHONE_VALIDATION_FAILED,
                ErrorConstants.PHONE_VALIDATION_FAILED_MESSAGE, e);
            return existingInCampaign; // unreachable (throwCustomException always throws)
        }

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

        log.info("Phone number validation completed");
        return existingInCampaign;
    }
    
    private void validateUserNames(List<Map<String, Object>> sheetData, String tenantId, 
                                 RequestInfo requestInfo, List<ValidationError> errors,
                                 Map<String, String> localizationMap, Set<String> existingPhonesInCampaign) {
        log.info("Validating usernames for {} records (will skip {} phones already in campaign)", 
                sheetData.size(), existingPhonesInCampaign.size());
        
        // Map usernames to their row numbers.
        // Use a LinkedHashSet for O(1) dedup (preserving insertion order) instead of
        // List.contains() in a loop, which is O(n^2) at large row counts.
        Map<String, Integer> userNameToRowMap = new HashMap<>();
        Set<String> uniqueUserNames = new LinkedHashSet<>();

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
                uniqueUserNames.add(userName);
            }
        }

        if (uniqueUserNames.isEmpty()) {
            log.info("No usernames to validate");
            return;
        }

        // Materialize as a List for the index-based batch (subList) operations below.
        List<String> allUserNames = new ArrayList<>(uniqueUserNames);
        
        // Search in bounded-parallel batches (size configurable; guard 0 from mocks/unset -> 50)
        final int BATCH_SIZE = config.getUserSearchBatchSize() > 0 ? config.getUserSearchBatchSize() : 50;
        List<Map<String, Object>> existingUsers;
        try {
            existingUsers = parallelBatchedSearch(allUserNames, BATCH_SIZE,
                    batch -> searchIndividualsByUsername(batch, tenantId, requestInfo));
        } catch (Exception e) {
            log.error("Error validating usernames: {}", e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.USERNAME_VALIDATION_FAILED,
                ErrorConstants.USERNAME_VALIDATION_FAILED_MESSAGE, e);
            return; // unreachable (throwCustomException always throws)
        }

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

        log.info("Username validation completed");
    }
    
    /** A single batched existence-search HTTP call. */
    @FunctionalInterface
    private interface BatchSearch {
        List<Map<String, Object>> search(List<String> batch) throws Exception;
    }

    /**
     * Runs {@code searchFn} over {@code values} in batches of {@code batchSize} with bounded parallelism
     * ({@code egov.excel.user.search.parallel.calls}, default 20), returning the merged results. At large
     * row counts the user existence checks are dozens-to-thousands of blocking 50-id HTTP calls; firing
     * them in bounded-parallel waves instead of strictly sequentially is the dominant scale win for
     * unified-console validation. Result ORDER is irrelevant - callers match rows by phone/username.
     * Mirrors the existing bounded-pool pattern in AttendanceRegisterValidationProcessor.
     */
    private List<Map<String, Object>> parallelBatchedSearch(List<String> values, int batchSize, BatchSearch searchFn) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < values.size(); i += batchSize) {
            // independent copies - subList views are not safe to share across threads
            batches.add(new ArrayList<>(values.subList(i, Math.min(i + batchSize, values.size()))));
        }
        if (batches.isEmpty()) {
            return new ArrayList<>();
        }
        int parallelCalls = Math.max(1, config.getUserSearchParallelCalls());
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(parallelCalls, batches.size()));
        List<Map<String, Object>> merged = Collections.synchronizedList(new ArrayList<>());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (List<String> batch : batches) {
                futures.add(executor.submit((Callable<Void>) () -> {
                    merged.addAll(searchFn.search(batch));
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("User existence search interrupted", e);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    throw new RuntimeException(cause.getMessage(), cause);
                }
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(120, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        return new ArrayList<>(merged);
    }

    private List<Map<String, Object>> searchIndividualsByMobileNumber(List<String> mobileNumbers,
                                                                     String tenantId,
                                                                     RequestInfo requestInfo) throws Exception {
        return searchIndividualsPaginated("mobileNumber", mobileNumbers, tenantId, requestInfo, true);
    }

    private List<Map<String, Object>> searchIndividualsByUsername(List<String> usernames,
                                                                String tenantId,
                                                                RequestInfo requestInfo) throws Exception {
        return searchIndividualsPaginated("username", usernames, tenantId, requestInfo, false);
    }

    /**
     * Searches health-individual for the given field values, PAGINATING results so a batch can never
     * silently truncate matches. The old code used a single fixed {@code limit} (~55) and dropped any
     * matches beyond it; with includeDeleted a phone/username can have several records, so a large batch
     * could miss existing users and let them pass. Here the page size is batch-size + buffer, so the
     * common case (matches &lt;= batch size, e.g. an all-new upload) is a SINGLE call (a short page ends
     * it); only a full page triggers another page. Bounded by {@code maxPages} for safety. This makes the
     * batch size safe to raise (round-trips drop) without a truncation risk.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> searchIndividualsPaginated(String field, List<String> values,
                                                                 String tenantId, RequestInfo requestInfo,
                                                                 boolean includeDeleted) throws Exception {
        String url = config.getHealthIndividualHost() + config.getHealthIndividualSearchPath();

        Map<String, Object> searchBody = new HashMap<>();
        searchBody.put("RequestInfo", requestInfo);
        Map<String, Object> individual = new HashMap<>();
        individual.put(field, values);
        searchBody.put("Individual", individual);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(searchBody, headers);

        final int pageSize = values.size() + 10;   // >= batch size: a full page => maybe more => paginate
        final int maxPages = 50;                    // safety bound; 50 pages is far beyond any real batch
        List<Map<String, Object>> all = new ArrayList<>();

        log.info("Searching individuals by {} ({} values, pageSize {})", field, values.size(), pageSize);
        for (int page = 0; page < maxPages; page++) {
            StringBuilder u = new StringBuilder(url)
                    .append("?tenantId=").append(tenantId)
                    .append("&limit=").append(pageSize)
                    .append("&offset=").append(page * pageSize);
            if (includeDeleted) {
                u.append("&includeDeleted=true");
            }
            ResponseEntity<Map> response = restTemplate.exchange(u.toString(), HttpMethod.POST, entity, Map.class);
            List<Map<String, Object>> pageRows = (response.getBody() != null && response.getBody().get("Individual") != null)
                    ? (List<Map<String, Object>>) response.getBody().get("Individual")
                    : new ArrayList<>();
            all.addAll(pageRows);
            if (pageRows.size() < pageSize) {
                break; // short page -> all matches retrieved
            }
        }
        return all;
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
     * Validate that each non-blank HCM_ADMIN_CONSOLE_USER_WORKER_ID exists in the worker registry.
     * Fail-closed: if the registry call fails, all rows in the failing batch are marked invalid.
     */
    private void validateWorkerIds(List<Map<String, Object>> sheetData,
                                    String tenantId,
                                    RequestInfo requestInfo,
                                    List<ValidationError> errors,
                                    Map<String, String> localizationMap) {
        log.info("Validating worker IDs for {} records", sheetData.size());

        Map<String, List<Integer>> workerIdToRowsMap = new HashMap<>();
        for (Map<String, Object> rowData : sheetData) {
            String workerId = ExcelUtil.getValueAsString(rowData.get(ProcessingConstants.WORKER_ID_COLUMN_KEY));
            if (workerId != null && !workerId.trim().isEmpty()) {
                workerIdToRowsMap.computeIfAbsent(workerId.trim(), k -> new ArrayList<>())
                        .add((Integer) rowData.get("__actualRowNumber__"));
            }
        }

        if (workerIdToRowsMap.isEmpty()) {
            log.info("No worker IDs to validate");
            return;
        }

        List<String> allWorkerIds = new ArrayList<>(workerIdToRowsMap.keySet());
        Set<String> foundWorkerIds = new HashSet<>();

        int batchSize = config.getWorkerRegistrySearchBatchSize();
        if (batchSize <= 0) {
            batchSize = 100;
        }
        for (int i = 0; i < allWorkerIds.size(); i += batchSize) {
            List<String> batch = allWorkerIds.subList(i, Math.min(i + batchSize, allWorkerIds.size()));
            try {
                List<Map<String, Object>> workers = searchWorkersByIds(batch, tenantId, requestInfo);
                for (Map<String, Object> worker : workers) {
                    String id = ExcelUtil.getValueAsString(worker.get("id"));
                    if (id != null && !id.isEmpty()) {
                        foundWorkerIds.add(id.trim());
                    }
                }
            } catch (Exception e) {
                log.error("Worker registry search failed for batch: {}", e.getMessage(), e);
                String errorMsg = LocalizationUtil.getLocalizedMessage(localizationMap,
                        ValidationConstants.LOC_USER_INVALID_WORKER_ID,
                        ValidationConstants.DEFAULT_USER_INVALID_WORKER_ID);
                for (String workerId : batch) {
                    List<Integer> rowNumbers = workerIdToRowsMap.get(workerId);
                    if (rowNumbers != null) {
                        for (Integer rowNumber : rowNumbers) {
                            ValidationError error = new ValidationError();
                            error.setRowNumber(rowNumber);
                            error.setErrorDetails(errorMsg);
                            error.setStatus(ValidationConstants.STATUS_INVALID);
                            error.setColumnName(ProcessingConstants.WORKER_ID_COLUMN_KEY);
                            errors.add(error);
                        }
                    }
                }
                foundWorkerIds.addAll(batch); // mark as handled to avoid duplicate errors
            }
        }

        String errorMsg = LocalizationUtil.getLocalizedMessage(localizationMap,
                ValidationConstants.LOC_USER_INVALID_WORKER_ID,
                ValidationConstants.DEFAULT_USER_INVALID_WORKER_ID);
        for (Map.Entry<String, List<Integer>> entry : workerIdToRowsMap.entrySet()) {
            if (!foundWorkerIds.contains(entry.getKey())) {
                for (Integer rowNumber : entry.getValue()) {
                    ValidationError error = new ValidationError();
                    error.setRowNumber(rowNumber);
                    error.setErrorDetails(errorMsg);
                    error.setStatus(ValidationConstants.STATUS_INVALID);
                    error.setColumnName(ProcessingConstants.WORKER_ID_COLUMN_KEY);
                    errors.add(error);
                }
            }
        }

        log.info("Worker ID validation completed");
    }

    /**
     * Validate that beneficiary code does not contain any whitespace characters.
     */
    private void validateBeneficiaryCode(List<Map<String, Object>> sheetData,
                                          List<ValidationError> errors,
                                          Map<String, String> localizationMap) {
        validateFieldNoWhitespace(sheetData, errors, localizationMap,
                ProcessingConstants.BENEFICIARY_CODE_COL,
                ValidationConstants.LOC_BENEFICIARY_CODE_WHITESPACE,
                ValidationConstants.DEFAULT_BENEFICIARY_CODE_WHITESPACE);
    }

    /**
     * Validate that the given field does not contain any whitespace characters.
     */
    private void validateFieldNoWhitespace(List<Map<String, Object>> sheetData,
                                            List<ValidationError> errors,
                                            Map<String, String> localizationMap,
                                            String columnKey,
                                            String localizationKey,
                                            String defaultMessage) {
        log.info("Validating {} for whitespace", columnKey);
        String errorMsg = LocalizationUtil.getLocalizedMessage(localizationMap, localizationKey, defaultMessage);

        for (Map<String, Object> rowData : sheetData) {
            String value = ExcelUtil.getValueAsString(rowData.get(columnKey));
            if (value == null || value.isEmpty()) continue;
            if (value.chars().anyMatch(Character::isWhitespace)) {
                ValidationError error = new ValidationError();
                error.setRowNumber((Integer) rowData.get("__actualRowNumber__"));
                error.setErrorDetails(errorMsg);
                error.setStatus(ValidationConstants.STATUS_INVALID);
                error.setColumnName(columnKey);
                errors.add(error);
            }
        }
        log.info("Whitespace validation completed for {}", columnKey);
    }

    private List<Map<String, Object>> searchWorkersByIds(List<String> workerIds,
                                                          String tenantId,
                                                          RequestInfo requestInfo) throws Exception {
        Map<String, Object> searchBody = new HashMap<>();
        searchBody.put("RequestInfo", requestInfo);

        Map<String, Object> workerSearch = new HashMap<>();
        workerSearch.put("id", workerIds);
        workerSearch.put("tenantId", tenantId);
        searchBody.put("workerSearch", workerSearch);

        StringBuilder urlWithParams = new StringBuilder(config.getWorkerRegistrySearchUrl())
                .append("?limit=").append(workerIds.size())
                .append("&offset=0&tenantId=").append(tenantId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(searchBody, headers);

        log.info("Searching worker registry for {} IDs", workerIds.size());
        ResponseEntity<Map> response = restTemplate.exchange(
                urlWithParams.toString(), HttpMethod.POST, entity, Map.class);

        if (response.getBody() != null && response.getBody().get("workers") != null) {
            return (List<Map<String, Object>>) response.getBody().get("workers");
        }
        return new ArrayList<>();
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