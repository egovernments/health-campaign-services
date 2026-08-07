package org.egov.excelingestion.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.egov.excelingestion.service.BoundaryService;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.CryptoService;
import org.egov.excelingestion.service.MDMSConfigService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.web.models.mdms.ExcelIngestionGenerateData;
import org.egov.excelingestion.web.models.*;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generator for attendance register attendee sheets (Worker, Marker, Approver).
 * Same class handles all 3 sheets — determines which rows to include based on sheetName.
 *
 * Flow:
 * 1. Fetch schema → ColumnDefs
 * 2. Fetch attendance register → localityCode
 * 3. Fetch boundary tree → resolve allowed codes from BoundaryFilterConfig in sheetConfig
 * 4. Fetch all completed user data for this campaign
 * 5. Classify users by role, filter by allowed boundary codes
 * 6. Decrypt credentials
 * 7. Return SheetGenerationResult
 *
 * Boundary filter modes (configured per sheet in MDMS):
 * - ANCESTOR_AND_SELF: register locality + all ancestors (Marker, Approver)
 * - LEVEL_RANGE: register locality down to configured deepest type (Worker)
 * - null/default: self only
 */
@Component
@Slf4j
public class AttendanceRegisterAttendeeSheetGenerator implements IExcelPopulatorSheetGenerator {

    // Sheet name keys — used for MDMS config lookup and worker-sheet detection
    private static final String WORKER_SHEET = "HCM_REGISTER_WORKER_SHEET";
    private static final String MARKER_SHEET = "HCM_REGISTER_MARKER_SHEET";
    private static final String APPROVER_SHEET = "HCM_REGISTER_APPROVER_SHEET";

    // Fallback role sets used when MDMS config is absent (safety net, avoids silent breakage)
    private static final Map<String, List<String>> FALLBACK_ROLE_CONFIG = Map.of(
            WORKER_SHEET,   List.of("DISTRIBUTOR", "REGISTRAR", "FIELD_SUPPORT", "HEALTH_FACILITY_WORKER"),
            MARKER_SHEET,   List.of("WAREHOUSE_MANAGER", "TEAM_SUPERVISOR", "CAMPAIGN_SUPERVISOR"),
            APPROVER_SHEET, List.of("PROXIMITY_SUPERVISOR")
    );

    // Sheet priority order: first match wins (APPROVER beats MARKER beats WORKER)
    private static final List<String> SHEET_PRIORITY = List.of(APPROVER_SHEET, MARKER_SHEET, WORKER_SHEET);

    private static final int MAX_MULTISELECT_COLUMNS = 5;
    private static final int BULK_DECRYPT_BATCH_SIZE = 500;

    private final MDMSService mdmsService;
    private final MDMSConfigService mdmsConfigService;
    private final CampaignService campaignService;
    private final BoundaryService boundaryService;
    private final CryptoService cryptoService;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ExcelIngestionConfig config;
    private final CustomExceptionHandler exceptionHandler;
    private final SchemaColumnDefUtil schemaColumnDefUtil;

    public AttendanceRegisterAttendeeSheetGenerator(
            MDMSService mdmsService,
            MDMSConfigService mdmsConfigService,
            CampaignService campaignService,
            BoundaryService boundaryService,
            CryptoService cryptoService,
            ServiceRequestRepository serviceRequestRepository,
            ExcelIngestionConfig config,
            CustomExceptionHandler exceptionHandler,
            SchemaColumnDefUtil schemaColumnDefUtil) {
        this.mdmsService = mdmsService;
        this.mdmsConfigService = mdmsConfigService;
        this.campaignService = campaignService;
        this.boundaryService = boundaryService;
        this.cryptoService = cryptoService;
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
        this.exceptionHandler = exceptionHandler;
        this.schemaColumnDefUtil = schemaColumnDefUtil;
    }

    @Override
    public SheetGenerationResult generateSheetData(SheetGenerationConfig sheetConfig,
                                                   GenerateResource generateResource,
                                                   RequestInfo requestInfo,
                                                   Map<String, String> localizationMap) {
        String sheetName = sheetConfig.getSheetName();
        String tenantId = generateResource.getTenantId();
        String referenceId = generateResource.getReferenceId();
        String hierarchyType = generateResource.getHierarchyType();

        log.info("Generating attendance register attendee sheet: {} for tenant: {}", sheetName, tenantId);

        // 1. Fetch schema from MDMS → convert to ColumnDefs
        List<ColumnDef> columnDefs = fetchSchemaColumnDefs(sheetConfig.getSchemaName(), tenantId, requestInfo);

        // 2. Fetch role config from MDMS (with fallback to hardcoded defaults)
        Map<String, List<String>> attendanceRoleConfig = fetchAttendanceRoleConfig(tenantId, requestInfo);
        // Build role → sheet map from MDMS config (priority order: APPROVER > MARKER > WORKER)
        Map<String, String> roleToSheetMap = buildRoleToSheetMap(attendanceRoleConfig);

        // 3. Resolve registerId (from referenceId when referenceType is attendanceRegister, else additionalDetails)
        String registerId = resolveRegisterId(generateResource);

        // 4. Fetch attendance register → get localityCode AND campaignNumber
        RegisterDetails registerDetails = fetchRegisterDetails(registerId, tenantId, requestInfo);
        String localityCode = registerDetails.localityCode;
        String campaignNumber = registerDetails.campaignNumber;

        // 5. Fetch boundary tree once and resolve allowed codes for this sheet's filter config
        BoundarySearchResponse boundaryResponse = boundaryService.fetchBoundaryRelationship(
                tenantId, hierarchyType, requestInfo);
        Set<String> allowedBoundaryCodes = resolveBoundaryFilterCodes(
                boundaryResponse, localityCode, sheetConfig.getBoundaryFilter());

        // 6. Fetch all completed user data for this campaign
        List<Map<String, Object>> allUsers = campaignService.searchCampaignDataByType(
                "user", ProcessingConstants.STATUS_COMPLETED, campaignNumber, tenantId, requestInfo);

        log.info("Fetched {} users for campaign {} in tenant {}", allUsers.size(), campaignNumber, tenantId);

        // 7. Classify, filter, and build rows for this sheet
        List<Map<String, Object>> filteredUsers = classifyAndFilterUsers(
                allUsers, sheetName, allowedBoundaryCodes, roleToSheetMap);

        log.info("Filtered {} users for sheet {}", filteredUsers.size(), sheetName);

        // 8. Decrypt credentials and build data rows
        List<Map<String, Object>> dataRows = buildDataRows(
                filteredUsers, registerDetails.serviceCode, localizationMap, requestInfo,
                WORKER_SHEET.equals(sheetName));

        return SheetGenerationResult.builder()
                .columnDefs(columnDefs)
                .data(dataRows.isEmpty() ? null : dataRows)
                .build();
    }

    /**
     * Fetch schema from MDMS and convert to ColumnDefs
     */
    private List<ColumnDef> fetchSchemaColumnDefs(String schemaName, String tenantId,
                                                  RequestInfo requestInfo) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("title", schemaName);

        List<Map<String, Object>> mdmsList = mdmsService.searchMDMS(
                requestInfo, tenantId, ProcessingConstants.MDMS_SCHEMA_CODE, filters, 1, 0);

        if (mdmsList.isEmpty()) {
            exceptionHandler.throwCustomException(ErrorConstants.MDMS_DATA_NOT_FOUND,
                    ErrorConstants.MDMS_DATA_NOT_FOUND_MESSAGE.replace("{0}", schemaName),
                    new RuntimeException("Schema not found: " + schemaName));
        }

        try {
            Map<String, Object> mdmsData = mdmsList.get(0);
            Object dataObj = mdmsData.get("data");
            if (!(dataObj instanceof Map)) {
                exceptionHandler.throwCustomException(ErrorConstants.INVALID_SCHEMA_FORMAT,
                        ErrorConstants.INVALID_SCHEMA_FORMAT_MESSAGE,
                        new RuntimeException("Schema record missing 'data' object: " + schemaName));
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) dataObj;
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) data.get("properties");

            ObjectMapper mapper = new ObjectMapper();
            String schemaJson = mapper.writeValueAsString(properties);
            return schemaColumnDefUtil.convertSchemaToColumnDefs(schemaJson);
        } catch (org.egov.tracer.model.CustomException ce) {
            // Preserve a structured error (e.g. INVALID_SCHEMA_FORMAT) instead of masking it.
            throw ce;
        } catch (Exception e) {
            log.error("Error extracting schema {}: {}", schemaName, e.getMessage(), e);
            exceptionHandler.throwCustomException(ErrorConstants.SCHEMA_CONVERSION_ERROR,
                    ErrorConstants.SCHEMA_CONVERSION_ERROR_MESSAGE, e);
            return Collections.emptyList(); // never reached
        }
    }

    /**
     * Resolve registerId: use referenceId when referenceType is attendanceRegister,
     * otherwise fall back to additionalDetails.registerId for backward compatibility.
     */
    private String resolveRegisterId(GenerateResource generateResource) {
        if ("attendanceRegister".equals(generateResource.getReferenceType())) {
            String registerId = generateResource.getReferenceId();
            if (registerId != null && !registerId.isBlank()) {
                return registerId.trim();
            }
        }

        // Fallback to additionalDetails.registerId
        Map<String, Object> additionalDetails = generateResource.getAdditionalDetails();
        if (additionalDetails == null || !additionalDetails.containsKey("registerId")) {
            exceptionHandler.throwCustomException(ErrorConstants.MISSING_REQUIRED_FIELD,
                    ErrorConstants.MISSING_REQUIRED_FIELD_MESSAGE.replace("{0}", "registerId"),
                    new RuntimeException("registerId is required in additionalDetails"));
        }
        Object registerIdObj = additionalDetails.get("registerId");
        if (registerIdObj == null || String.valueOf(registerIdObj).isBlank()) {
            exceptionHandler.throwCustomException(ErrorConstants.MISSING_REQUIRED_FIELD,
                    ErrorConstants.MISSING_REQUIRED_FIELD_MESSAGE.replace("{0}", "registerId"),
                    new RuntimeException("registerId value is null or empty in additionalDetails"));
        }
        return String.valueOf(registerIdObj).trim();
    }

    /**
     * Fetch attendance register by ID and extract localityCode and campaignNumber
     */
    private RegisterDetails fetchRegisterDetails(String registerId, String tenantId,
                                                  RequestInfo requestInfo) {
        StringBuilder url = new StringBuilder(config.getAttendanceRegisterSearchUrl());
        url.append("?tenantId=").append(tenantId)
           .append("&ids=").append(registerId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("RequestInfo", requestInfo);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) serviceRequestRepository.fetchResult(url, payload);

        if (response == null || response.get("attendanceRegister") == null) {
            exceptionHandler.throwCustomException(ErrorConstants.ATTENDANCE_REGISTER_NOT_FOUND,
                    ErrorConstants.ATTENDANCE_REGISTER_NOT_FOUND_MESSAGE.replace("{0}", registerId),
                    new RuntimeException("Attendance register not found: " + registerId));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> registers =
                (List<Map<String, Object>>) response.get("attendanceRegister");

        if (registers.isEmpty()) {
            exceptionHandler.throwCustomException(ErrorConstants.ATTENDANCE_REGISTER_NOT_FOUND,
                    ErrorConstants.ATTENDANCE_REGISTER_NOT_FOUND_MESSAGE.replace("{0}", registerId),
                    new RuntimeException("Attendance register not found: " + registerId));
        }

        Map<String, Object> register = registers.get(0);

        String localityCode = extractLocalityCode(register);
        if (localityCode == null || localityCode.isBlank()) {
            exceptionHandler.throwCustomException(ErrorConstants.ATTENDANCE_REGISTER_LOCALITY_MISSING,
                    ErrorConstants.ATTENDANCE_REGISTER_LOCALITY_MISSING_MESSAGE.replace("{0}", registerId),
                    new RuntimeException("localityCode missing on register: " + registerId));
        }

        String campaignNumber = extractCampaignNumber(register);
        if (campaignNumber == null || campaignNumber.isBlank()) {
            exceptionHandler.throwCustomException(ErrorConstants.MISSING_REQUIRED_FIELD,
                    ErrorConstants.MISSING_REQUIRED_FIELD_MESSAGE.replace("{0}", "campaignNumber"),
                    new RuntimeException("campaignNumber missing on attendance register: " + registerId));
        }

        String serviceCode = register.get("serviceCode") != null ? String.valueOf(register.get("serviceCode")).trim() : "";

        log.info("Attendance register {} has localityCode: {}, campaignNumber: {}, serviceCode: {}", registerId, localityCode, campaignNumber, serviceCode);
        return new RegisterDetails(localityCode, campaignNumber, serviceCode);
    }

    /**
     * Extract campaignNumber from attendance register response.
     * Checks top-level campaignNumber first, then additionalDetails.campaignNumber.
     */
    private String extractCampaignNumber(Map<String, Object> register) {
        Object topLevel = register.get("campaignNumber");
        if (topLevel != null) {
            String number = String.valueOf(topLevel).trim();
            if (!number.isEmpty()) return number;
        }

        Object additionalDetailsObj = register.get("additionalDetails");
        if (additionalDetailsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> additionalDetails = (Map<String, Object>) additionalDetailsObj;
            Object campaignNumberObj = additionalDetails.get("campaignNumber");
            if (campaignNumberObj != null) {
                String number = String.valueOf(campaignNumberObj).trim();
                if (!number.isEmpty()) return number;
            }
        }

        return null;
    }

    /**
     * Holds localityCode, campaignNumber, and serviceCode extracted from an attendance register
     */
    private static class RegisterDetails {
        final String localityCode;
        final String campaignNumber;
        final String serviceCode;

        RegisterDetails(String localityCode, String campaignNumber, String serviceCode) {
            this.localityCode = localityCode;
            this.campaignNumber = campaignNumber;
            this.serviceCode = serviceCode;
        }
    }

    /**
     * Extract localityCode from attendance register response.
     * Checks additionalDetails.localityCode first, then top-level localityCode.
     */
    private String extractLocalityCode(Map<String, Object> register) {
        // Check additionalDetails.localityCode first
        Object additionalDetailsObj = register.get("additionalDetails");
        if (additionalDetailsObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> additionalDetails = (Map<String, Object>) additionalDetailsObj;
            Object locality = additionalDetails.get("localityCode");
            if (locality != null) {
                String code = String.valueOf(locality).trim();
                if (!code.isEmpty()) return code;
            }
        }

        // Fallback to top-level localityCode
        Object topLevel = register.get("localityCode");
        if (topLevel != null) {
            String code = String.valueOf(topLevel).trim();
            if (!code.isEmpty()) return code;
        }

        return null;
    }

    /**
     * Resolve the set of allowed boundary codes for this sheet based on BoundaryFilterConfig.
     *
     * Modes:
     * - ANCESTOR_AND_SELF: register locality + all ancestors (path from root to locality)
     * - LEVEL_RANGE: register locality down to the configured deepest boundary type
     * - null/unknown: self only (register locality exact match)
     */
    private Set<String> resolveBoundaryFilterCodes(BoundarySearchResponse boundaryResponse,
                                                    String localityCode,
                                                    BoundaryFilterConfig filter) {
        if (filter == null || filter.getMode() == null) {
            return Set.of(localityCode);
        }
        switch (filter.getMode()) {
            case "ANCESTOR_AND_SELF":
                return extractAncestorAndSelfCodes(boundaryResponse, localityCode);
            case "LEVEL_RANGE":
                return extractLevelRangeCodes(boundaryResponse, localityCode, filter.getLevelConfig());
            default:
                log.warn("Unknown boundaryFilter mode '{}', defaulting to self only", filter.getMode());
                return Set.of(localityCode);
        }
    }

    /**
     * Collect all boundary codes along the path from root to localityCode (ancestors + self).
     * Returns Set containing the locality and all its ancestors.
     * Fallback: Set.of(localityCode) if not found in tree.
     */
    private Set<String> extractAncestorAndSelfCodes(BoundarySearchResponse boundaryResponse, String localityCode) {
        if (boundaryResponse != null && boundaryResponse.getTenantBoundary() != null) {
            for (HierarchyRelation relation : boundaryResponse.getTenantBoundary()) {
                if (relation.getBoundary() != null) {
                    for (EnrichedBoundary root : relation.getBoundary()) {
                        Set<String> ancestorCodes = new HashSet<>();
                        if (collectAncestorPath(root, localityCode, ancestorCodes)) {
                            log.info("Found {} ancestor+self codes for locality {}", ancestorCodes.size(), localityCode);
                            return ancestorCodes;
                        }
                    }
                }
            }
        }
        log.warn("Locality {} not found in boundary tree for ANCESTOR_AND_SELF, using self only", localityCode);
        return new HashSet<>(Set.of(localityCode));
    }

    /**
     * DFS tracking path from root. Adds codes to result when target is found.
     * Returns true if target was found in this subtree.
     */
    private boolean collectAncestorPath(EnrichedBoundary node, String targetCode, Set<String> result) {
        if (node == null) return false;
        if (targetCode.equals(node.getCode())) {
            if (node.getCode() != null) result.add(node.getCode());
            return true;
        }
        if (node.getChildren() != null) {
            for (EnrichedBoundary child : node.getChildren()) {
                if (collectAncestorPath(child, targetCode, result)) {
                    if (node.getCode() != null) result.add(node.getCode()); // add self as ancestor
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Collect boundary codes from register locality down to configured deepest boundary type.
     * If no levelConfig entry for the register's boundary type, returns self only.
     */
    private Set<String> extractLevelRangeCodes(BoundarySearchResponse boundaryResponse,
                                                String localityCode,
                                                Map<String, String> levelConfig) {
        if (boundaryResponse != null && boundaryResponse.getTenantBoundary() != null) {
            for (HierarchyRelation relation : boundaryResponse.getTenantBoundary()) {
                if (relation.getBoundary() != null) {
                    for (EnrichedBoundary root : relation.getBoundary()) {
                        EnrichedBoundary localityNode = findBoundaryNode(root, localityCode);
                        if (localityNode != null) {
                            String registerBoundaryType = localityNode.getBoundaryType();
                            String deepestType = levelConfig != null ? levelConfig.get(registerBoundaryType) : null;
                            if (deepestType == null) {
                                log.info("No levelConfig entry for boundary type '{}', using self only for locality {}",
                                        registerBoundaryType, localityCode);
                                return Set.of(localityCode);
                            }
                            Set<String> codes = new HashSet<>();
                            collectDescendantsUntilLevel(localityNode, deepestType, codes);
                            log.info("LEVEL_RANGE: {} codes for locality {} (type={}, deepest={})",
                                    codes.size(), localityCode, registerBoundaryType, deepestType);
                            return codes;
                        }
                    }
                }
            }
        }
        log.warn("Locality {} not found in boundary tree for LEVEL_RANGE, using self only", localityCode);
        return new HashSet<>(Set.of(localityCode));
    }

    /**
     * Find a boundary node by code in the tree (DFS)
     */
    private EnrichedBoundary findBoundaryNode(EnrichedBoundary node, String targetCode) {
        if (node == null) return null;
        if (targetCode.equals(node.getCode())) return node;
        if (node.getChildren() != null) {
            for (EnrichedBoundary child : node.getChildren()) {
                EnrichedBoundary found = findBoundaryNode(child, targetCode);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * DFS: collect codes from node downward, stopping recursion when deepestType is reached.
     * Nodes at deepestType are included but their children are not.
     */
    private void collectDescendantsUntilLevel(EnrichedBoundary node, String deepestType, Set<String> codes) {
        if (node == null) return;
        if (node.getCode() != null) codes.add(node.getCode());
        if (deepestType.equals(node.getBoundaryType())) return; // stop, don't recurse deeper
        if (node.getChildren() != null) {
            for (EnrichedBoundary child : node.getChildren()) {
                collectDescendantsUntilLevel(child, deepestType, codes);
            }
        }
    }

    /**
     * Classify users by role and filter by allowed boundary codes for the current sheet.
     * A user appears in at most one sheet — role priority order in SHEET_PRIORITY determines which sheet wins.
     * roleToSheetMap is derived from MDMS attendanceRoleConfig (or fallback defaults).
     */
    private List<Map<String, Object>> classifyAndFilterUsers(
            List<Map<String, Object>> allUsers, String sheetName,
            Set<String> allowedBoundaryCodes, Map<String, String> roleToSheetMap) {

        List<Map<String, Object>> filtered = new ArrayList<>();

        for (Map<String, Object> userEntry : allUsers) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rawData = (Map<String, Object>) userEntry.get("data");
            if (rawData == null) continue;
            String userName = getStringValue(rawData, "UserName");
            if (userName.isEmpty()) continue;

            List<String> roleCodes = getRoleCodes(rawData);
            String classifiedSheet = classifyUserToSheet(roleCodes, roleToSheetMap);
            if (classifiedSheet == null || !classifiedSheet.equals(sheetName)) continue;

            String boundaryCode = getStringValue(rawData, "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY");
            if (boundaryCode.isEmpty()) {
                boundaryCode = getStringValue(rawData, "HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
            }

            if (!allowedBoundaryCodes.contains(boundaryCode)) continue;

            filtered.add(userEntry);
        }

        return filtered;
    }

    /**
     * Build data rows from filtered users with decrypted credentials
     */
    private List<Map<String, Object>> buildDataRows(
            List<Map<String, Object>> filteredUsers, String registerServiceCode,
            Map<String, String> localizationMap, RequestInfo requestInfo,
            boolean includeTeamCode) {

        if (filteredUsers.isEmpty()) return Collections.emptyList();

        // Collect unique encrypted strings for bulk decryption
        Set<String> encryptedSet = new LinkedHashSet<>();
        for (Map<String, Object> userEntry : filteredUsers) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rawData = (Map<String, Object>) userEntry.get("data");
            String userName = getStringValue(rawData, "UserName");
            String password = getStringValue(rawData, "Password");
            if (!userName.isEmpty()) encryptedSet.add(userName);
            if (!password.isEmpty()) encryptedSet.add(password);
        }

        // Bulk decrypt in batches
        Map<String, String> decryptedMap = bulkDecryptInBatches(
                new ArrayList<>(encryptedSet), requestInfo);

        // Build data rows
        List<Map<String, Object>> dataRows = new ArrayList<>();

        for (Map<String, Object> userEntry : filteredUsers) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rawData = (Map<String, Object>) userEntry.get("data");

            String boundaryCode = getStringValue(rawData, "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY");
            if (boundaryCode.isEmpty()) {
                boundaryCode = getStringValue(rawData, "HCM_ADMIN_CONSOLE_BOUNDARY_CODE");
            }

            String encryptedUserName = getStringValue(rawData, "UserName");
            String encryptedPassword = getStringValue(rawData, "Password");
            List<String> roleCodes = getRoleCodes(rawData);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("HCM_ADMIN_CONSOLE_USER_WORKER_ID",
                    getStringValue(rawData, "HCM_ADMIN_CONSOLE_USER_WORKER_ID"));
            row.put("HCM_ADMIN_CONSOLE_USER_NAME",
                    getStringValue(rawData, "HCM_ADMIN_CONSOLE_USER_NAME"));
            row.put("UserName", decryptedMap.getOrDefault(encryptedUserName, encryptedUserName));
            row.put("Password", decryptedMap.getOrDefault(encryptedPassword, encryptedPassword));
            row.put("HCM_ADMIN_CONSOLE_USER_ROLE", String.join(", ", roleCodes));
            row.put("HCM_ADMIN_CONSOLE_BOUNDARY_NAME",
                    getStringValueOrLocalized(rawData, "HCM_ADMIN_CONSOLE_BOUNDARY_NAME",
                            boundaryCode, localizationMap));
            row.put("HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY", boundaryCode);
            row.put("HCM_ATTENDANCE_REGISTER_ID", registerServiceCode);
            row.put("HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE", "");
            row.put("HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE", "");

            if (includeTeamCode) {
                row.put("HCM_ATTENDANCE_ATTENDEE_TEAM_CODE", "");
            }

            dataRows.add(row);
        }

        return dataRows;
    }

    /**
     * Bulk decrypt encrypted strings in batches
     */
    private Map<String, String> bulkDecryptInBatches(List<String> encryptedStrings,
                                                      RequestInfo requestInfo) {
        if (encryptedStrings.isEmpty()) return Collections.emptyMap();

        Map<String, String> decryptedMap = new HashMap<>();

        for (int i = 0; i < encryptedStrings.size(); i += BULK_DECRYPT_BATCH_SIZE) {
            int end = Math.min(i + BULK_DECRYPT_BATCH_SIZE, encryptedStrings.size());
            List<String> batch = encryptedStrings.subList(i, end);

            try {
                List<String> decryptedBatch = cryptoService.bulkDecrypt(batch, requestInfo);
                for (int j = 0; j < batch.size(); j++) {
                    decryptedMap.put(batch.get(j), decryptedBatch.get(j));
                }
            } catch (Exception e) {
                // Do NOT fall back to the encrypted values - that would silently ship
                // unusable ciphertext into the sheet as UserName/Password. Fail loud so the
                // generation is marked FAILED and the user can retry.
                log.error("Error decrypting credential batch starting at index {}: {}", i, e.getMessage(), e);
                exceptionHandler.throwCustomException(ErrorConstants.CREDENTIAL_DECRYPTION_ERROR,
                        ErrorConstants.CREDENTIAL_DECRYPTION_ERROR_MESSAGE, e);
            }
        }

        return decryptedMap;
    }

    /**
     * Read role codes from user data.
     * Tries the base role column first; falls back to individual multiselect columns (1-n).
     */
    private List<String> getRoleCodes(Map<String, Object> rawData) {
        String baseRole = getStringValue(rawData, "HCM_ADMIN_CONSOLE_USER_ROLE");
        if (!baseRole.isBlank()) {
            List<String> roles = new ArrayList<>();
            for (String r : baseRole.split(",")) {
                String trimmed = r.trim().toUpperCase();
                if (!trimmed.isEmpty()) roles.add(trimmed);
            }
            return roles;
        }

        // Fallback: read individual multiselect columns
        List<String> codes = new ArrayList<>();
        for (int i = 1; i <= MAX_MULTISELECT_COLUMNS; i++) {
            String val = getStringValue(rawData, "HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_" + i);
            if (!val.isBlank()) {
                codes.add(val.trim().toUpperCase());
            }
        }
        return codes;
    }

    /**
     * Classify user to sheet based on role codes using the MDMS-driven roleToSheetMap.
     * Priority is determined by SHEET_PRIORITY order (APPROVER > MARKER > WORKER).
     * Returns null if no role matches any known sheet.
     */
    private String classifyUserToSheet(List<String> roleCodes, Map<String, String> roleToSheetMap) {
        // Walk priority order — return the highest-priority sheet for which any role matches
        for (String prioritySheet : SHEET_PRIORITY) {
            for (String role : roleCodes) {
                if (prioritySheet.equals(roleToSheetMap.get(role))) {
                    return prioritySheet;
                }
            }
        }
        return null;
    }

    /**
     * Build role → sheet map from MDMS attendanceRoleConfig.
     * Iterates SHEET_PRIORITY (APPROVER → MARKER → WORKER) and uses putIfAbsent so the
     * first (highest-priority) sheet wins for any role that appears in multiple sheets.
     */
    private Map<String, String> buildRoleToSheetMap(Map<String, List<String>> attendanceRoleConfig) {
        Map<String, String> roleToSheet = new HashMap<>();
        // First sheet in priority order wins — putIfAbsent skips subsequent lower-priority mappings
        for (String sheet : SHEET_PRIORITY) {
            List<String> roles = attendanceRoleConfig.getOrDefault(sheet, Collections.emptyList());
            for (String role : roles) {
                roleToSheet.putIfAbsent(role, sheet);
            }
        }
        return roleToSheet;
    }

    /**
     * Fetch attendanceRoleConfig from MDMS generate config.
     * Falls back to FALLBACK_ROLE_CONFIG if MDMS returns no data.
     */
    private Map<String, List<String>> fetchAttendanceRoleConfig(String tenantId, RequestInfo requestInfo) {
        try {
            ExcelIngestionGenerateData generateData = mdmsConfigService.getExcelIngestionGenerateConfig(
                    requestInfo, tenantId,
                    ProcessingConstants.MDMS_ATTENDANCE_REGISTER_ATTENDEE_CONFIG_NAME);
            if (generateData != null && !generateData.getAttendanceRoleConfig().isEmpty()) {
                log.info("Loaded attendanceRoleConfig from MDMS for tenant {}", tenantId);
                return generateData.getAttendanceRoleConfig();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch attendanceRoleConfig from MDMS for tenant {}: {}", tenantId, e.getMessage());
        }
        log.warn("Using fallback attendanceRoleConfig for tenant {}", tenantId);
        return FALLBACK_ROLE_CONFIG;
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value).trim() : "";
    }

    private String getStringValueOrLocalized(Map<String, Object> rawData, String key,
                                              String boundaryCode, Map<String, String> localizationMap) {
        String value = getStringValue(rawData, key);
        if (!value.isEmpty()) return value;
        if (localizationMap != null && boundaryCode != null) {
            return localizationMap.getOrDefault(boundaryCode, boundaryCode);
        }
        return boundaryCode != null ? boundaryCode : "";
    }
}
