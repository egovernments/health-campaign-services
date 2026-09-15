package org.egov.excelingestion.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.egov.excelingestion.service.CampaignService;
import org.egov.excelingestion.service.MDMSService;
import org.egov.excelingestion.util.RequestInfoUtil;
import org.egov.excelingestion.util.SchemaColumnDefUtil;
import org.egov.excelingestion.web.models.CampaignSearchResponse;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.SheetGenerationConfig;
import org.egov.excelingestion.web.models.SheetGenerationResult;
import org.egov.excelingestion.web.models.excel.ColumnDef;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Generates one bulk mapping sheet with one row per register-user mapping.
 * Includes register-only rows (blank user fields) when a register has no mappings.
 */
@Component
@Slf4j
public class AttendanceRegisterUserBulkMappingSheetGenerator implements IExcelPopulatorSheetGenerator {

    private static final int REGISTER_SEARCH_LIMIT = 200;
    private static final int INDIVIDUAL_SEARCH_BATCH_SIZE = 100;
    private static final int MAX_ROLE_COLUMNS = 5;
    private static final DateTimeFormatter SHEET_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String STAFF_TYPE_APPROVER = "APPROVER";
    private static final String STAFF_TYPE_OWNER = "OWNER";
    private static final String ROLE_WORKER = "WORKER";
    private static final String ROLE_MARKER = "TEAM_SUPERVISOR";
    private static final String ROLE_APPROVER = "PROXIMITY_SUPERVISOR";

    private static final String ATTENDEE_DATA_TYPE = "attendanceRegisterAttendee";

    private static final String REGISTER_CODE_COLUMN = "HCM_ATTENDANCE_REGISTER_CODE";
    private static final String REGISTER_NAME_COLUMN = "HCM_ATTENDANCE_REGISTER_NAME";
    private static final String REGISTER_UUID_COLUMN = "HCM_ATTENDANCE_REGISTER_UUID";
    private static final String USER_NAME_COLUMN = "HCM_ADMIN_CONSOLE_USER_NAME";
    private static final String WORKER_ID_COLUMN = "HCM_ADMIN_CONSOLE_USER_WORKER_ID";
    private static final String ROLE_COLUMN = "HCM_ADMIN_CONSOLE_USER_ROLE";
    private static final String BOUNDARY_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_NAME";
    private static final String BOUNDARY_CODE_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE";
    private static final String BOUNDARY_CODE_MANDATORY_COLUMN = "HCM_ADMIN_CONSOLE_BOUNDARY_CODE_MANDATORY";
    private static final String USERNAME_COLUMN = "UserName";
    private static final String ENROLLMENT_DATE_COLUMN = "HCM_ATTENDANCE_ATTENDEE_ENROLLMENT_DATE";
    private static final String DEENROLLMENT_DATE_COLUMN = "HCM_ATTENDANCE_ATTENDEE_DEENROLLMENT_DATE";

    private final MDMSService mdmsService;
    private final CampaignService campaignService;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ExcelIngestionConfig config;
    private final CustomExceptionHandler exceptionHandler;
    private final SchemaColumnDefUtil schemaColumnDefUtil;

    public AttendanceRegisterUserBulkMappingSheetGenerator(
            MDMSService mdmsService,
            CampaignService campaignService,
            ServiceRequestRepository serviceRequestRepository,
            ExcelIngestionConfig config,
            CustomExceptionHandler exceptionHandler,
            SchemaColumnDefUtil schemaColumnDefUtil) {
        this.mdmsService = mdmsService;
        this.campaignService = campaignService;
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
        String tenantId = generateResource.getTenantId();
        requestInfo = RequestInfoUtil.ensureUserInfo(requestInfo, tenantId);

        List<ColumnDef> columnDefs = fetchSchemaColumnDefs(sheetConfig.getSchemaName(), tenantId, requestInfo);
        String campaignId = resolveCampaignId(generateResource);

        CampaignSearchResponse.CampaignDetail campaign =
                campaignService.searchCampaignById(campaignId, tenantId, requestInfo);
        String campaignNumber = stringValue(campaign.getCampaignNumber());
        if (campaignNumber.isBlank()) {
            exceptionHandler.throwCustomException(
                    ErrorConstants.MISSING_REQUIRED_FIELD,
                    ErrorConstants.MISSING_REQUIRED_FIELD_MESSAGE.replace("{0}", "campaignNumber"),
                    new RuntimeException("campaignNumber missing on campaign: " + campaignId));
        }

        String campaignStartDate = formatEpochIfPresent(campaign.getStartDate());
        String campaignEndDate = formatEpochIfPresent(campaign.getEndDate());

        List<RegisterData> registers = fetchCampaignRegisters(campaignId, tenantId, requestInfo);
        Map<String, RegisterData> registerByServiceCode = new HashMap<>();
        Map<String, RegisterData> registerById = new HashMap<>();
        for (RegisterData register : registers) {
            registerByServiceCode.put(register.serviceCode, register);
            registerById.put(register.id, register);
        }

        List<Map<String, Object>> attendeeRows = campaignService.searchCampaignDataByType(
                ATTENDEE_DATA_TYPE,
                ProcessingConstants.STATUS_COMPLETED,
                campaignNumber,
                tenantId,
                requestInfo
        );

        List<Map<String, Object>> rows = buildBulkRows(
                registers,
                attendeeRows,
                registerByServiceCode,
                registerById,
                campaignStartDate,
                campaignEndDate
        );

        if (!containsMappedRows(rows)) {
            rows = buildRowsFromAttendanceState(
                    registers,
                    tenantId,
                    requestInfo,
                    campaignStartDate,
                    campaignEndDate
            );
        }

        log.info("Bulk mapping sheet generated: campaignId={}, registers={}, rows={}",
                campaignId, registers.size(), rows.size());

        return SheetGenerationResult.builder()
                .columnDefs(columnDefs)
                .data(rows.isEmpty() ? null : rows)
                .build();
    }

    List<Map<String, Object>> buildBulkRows(
            List<RegisterData> registers,
            List<Map<String, Object>> attendeeRows,
            Map<String, RegisterData> registerByServiceCode,
            Map<String, RegisterData> registerById,
            String campaignStartDate,
            String campaignEndDate
    ) {
        Map<String, Map<String, Object>> dedupedRows = new LinkedHashMap<>();
        Set<String> mappedRegisters = new HashSet<>();

        for (Map<String, Object> attendeeRow : attendeeRows) {
            if (isDeleted(attendeeRow)) continue;

            Map<String, Object> rawData = asMap(attendeeRow.get("data"));
            if (rawData == null) continue;

            RegisterData register = resolveRegister(rawData, stringValue(attendeeRow.get("uniqueIdAfterProcess")),
                    registerByServiceCode, registerById);
            if (register == null) continue;

            String registerKey = register.identity();
            mappedRegisters.add(registerKey);

            Map<String, Object> row = buildMappedRow(
                    register,
                    rawData,
                    getLongValue(attendeeRow.get("denrollmentDate")),
                    campaignStartDate,
                    campaignEndDate
            );

            String personKey = personIdentity(rawData, stringValue(attendeeRow.get("uniqueIdentifier")));
            String dedupeKey = registerKey + "::" + personKey;
            Map<String, Object> existing = dedupedRows.get(dedupeKey);
            dedupedRows.put(dedupeKey, existing == null ? row : mergeRows(existing, row));
        }

        for (RegisterData register : registers) {
            String registerKey = register.identity();
            if (mappedRegisters.contains(registerKey)) continue;
            dedupedRows.put(
                    registerKey + "::__register_only__",
                    buildRegisterOnlyRow(register, campaignStartDate, campaignEndDate)
            );
        }

        List<Map<String, Object>> rows = new ArrayList<>(dedupedRows.values());
        rows.sort((left, right) ->
                compareByColumn(left, right, REGISTER_CODE_COLUMN,
                        USER_NAME_COLUMN, WORKER_ID_COLUMN));
        return rows;
    }

    private List<RegisterData> fetchCampaignRegisters(String campaignId, String tenantId, RequestInfo requestInfo) {
        List<RegisterData> registers = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int offset = 0; offset <= 10000; offset += REGISTER_SEARCH_LIMIT) {
            StringBuilder url = new StringBuilder(config.getAttendanceRegisterSearchUrl());
            url.append("?tenantId=").append(tenantId)
                    .append("&referenceId=").append(campaignId)
                    .append("&limit=").append(REGISTER_SEARCH_LIMIT)
                    .append("&offset=").append(offset);

            Map<String, Object> payload = new HashMap<>();
            payload.put("RequestInfo", requestInfo);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = serviceRequestRepository.fetchResult(url, payload, Map.class);
            List<Map<String, Object>> batch = asMapList(response != null ? response.get("attendanceRegister") : null);
            if (batch.isEmpty()) break;

            for (Map<String, Object> registerMap : batch) {
                if (Boolean.TRUE.equals(registerMap.get("isDeleted"))) continue;

                String id = stringValue(registerMap.get("id"));
                String serviceCode = stringValue(registerMap.get("serviceCode"));
                if (id.isBlank() || serviceCode.isBlank()) continue;

                String key = id + "::" + serviceCode;
                if (seen.contains(key)) continue;
                seen.add(key);

                registers.add(new RegisterData(
                        id,
                        serviceCode,
                        firstNonBlank(stringValue(registerMap.get("name")), serviceCode),
                        stringValue(registerMap.get("localityCode")),
                        asMapList(registerMap.get("attendees")),
                        asMapList(registerMap.get("staff"))
                ));
            }

            if (batch.size() < REGISTER_SEARCH_LIMIT) break;
        }

        return registers;
    }

    private boolean containsMappedRows(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            if (!firstNonBlank(
                    stringValue(row.get(USER_NAME_COLUMN)),
                    stringValue(row.get(WORKER_ID_COLUMN))
            ).isBlank()) {
                return true;
            }
        }
        return false;
    }

    List<Map<String, Object>> buildRowsFromAttendanceState(
            List<RegisterData> registers,
            String tenantId,
            RequestInfo requestInfo,
            String campaignStartDate,
            String campaignEndDate
    ) {
        if (registers.isEmpty()) return Collections.emptyList();

        Set<String> personIds = new LinkedHashSet<>();
        for (RegisterData register : registers) {
            for (Map<String, Object> attendee : register.attendees) {
                String personId = stringValue(attendee.get("individualId"));
                if (!personId.isBlank()) personIds.add(personId);
            }
            for (Map<String, Object> staff : register.staff) {
                String personId = stringValue(staff.get("userId"));
                if (!personId.isBlank()) personIds.add(personId);
            }
        }

        Map<String, IndividualProfile> profiles = fetchIndividualProfiles(tenantId, requestInfo, personIds);
        Map<String, Map<String, Object>> dedupedRows = new LinkedHashMap<>();
        Set<String> mappedRegisters = new HashSet<>();

        for (RegisterData register : registers) {
            String registerKey = register.identity();

            for (Map<String, Object> attendee : register.attendees) {
                String personId = stringValue(attendee.get("individualId"));
                if (personId.isBlank()) continue;

                IndividualProfile profile = profiles.get(personId);
                String role = ROLE_WORKER;
                String dedupeKey = registerKey + "::" + personId + "::" + role;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put(REGISTER_CODE_COLUMN, register.serviceCode);
                row.put(REGISTER_NAME_COLUMN, register.name);
                row.put(REGISTER_UUID_COLUMN, register.id);
                row.put(USER_NAME_COLUMN, firstNonBlank(
                        profile != null ? profile.displayName : "",
                        profile != null ? profile.username : "",
                        personId
                ));
                row.put(WORKER_ID_COLUMN, personId);
                row.put(ROLE_COLUMN, role);
                row.put(BOUNDARY_COLUMN, register.localityCode);
                row.put(ENROLLMENT_DATE_COLUMN, firstNonBlank(
                        formatEpochIfPresent(getLongValue(attendee.get("enrollmentDate"))),
                        campaignStartDate
                ));
                row.put(DEENROLLMENT_DATE_COLUMN, firstNonBlank(
                        formatEpochIfPresent(getLongValue(attendee.get("denrollmentDate"))),
                        campaignEndDate
                ));

                dedupedRows.put(dedupeKey, row);
                mappedRegisters.add(registerKey);
            }

            for (Map<String, Object> staff : register.staff) {
                String personId = stringValue(staff.get("userId"));
                if (personId.isBlank()) continue;

                String role = normalizeRoleFromStaffType(staff.get("staffType"));
                if (role.isBlank()) continue;

                IndividualProfile profile = profiles.get(personId);
                String staffName = displayNameFromStaff(staff);
                String dedupeKey = registerKey + "::" + personId + "::" + role;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put(REGISTER_CODE_COLUMN, register.serviceCode);
                row.put(REGISTER_NAME_COLUMN, register.name);
                row.put(REGISTER_UUID_COLUMN, register.id);
                row.put(USER_NAME_COLUMN, firstNonBlank(
                        profile != null ? profile.displayName : "",
                        staffName,
                        profile != null ? profile.username : "",
                        personId
                ));
                row.put(WORKER_ID_COLUMN, personId);
                row.put(ROLE_COLUMN, role);
                row.put(BOUNDARY_COLUMN, register.localityCode);
                row.put(ENROLLMENT_DATE_COLUMN, firstNonBlank(
                        formatEpochIfPresent(getLongValue(staff.get("enrollmentDate"))),
                        campaignStartDate
                ));
                row.put(DEENROLLMENT_DATE_COLUMN, firstNonBlank(
                        formatEpochIfPresent(getLongValue(staff.get("denrollmentDate"))),
                        campaignEndDate
                ));

                dedupedRows.put(dedupeKey, row);
                mappedRegisters.add(registerKey);
            }
        }

        for (RegisterData register : registers) {
            String registerKey = register.identity();
            if (mappedRegisters.contains(registerKey)) continue;
            dedupedRows.put(registerKey + "::__register_only__",
                    buildRegisterOnlyRow(register, campaignStartDate, campaignEndDate));
        }

        List<Map<String, Object>> rows = new ArrayList<>(dedupedRows.values());
        rows.sort((left, right) ->
                compareByColumn(left, right, REGISTER_CODE_COLUMN, USER_NAME_COLUMN, WORKER_ID_COLUMN));
        return rows;
    }

    private RegisterData resolveRegister(Map<String, Object> rawData,
                                         String uniqueIdAfterProcess,
                                         Map<String, RegisterData> registerByServiceCode,
                                         Map<String, RegisterData> registerById) {
        String serviceCode = firstNonBlank(
                stringValue(rawData.get("_registerServiceCode")),
                stringValue(rawData.get("HCM_ATTENDANCE_REGISTER_ID")),
                stringValue(rawData.get(REGISTER_CODE_COLUMN))
        );
        if (!serviceCode.isBlank() && registerByServiceCode.containsKey(serviceCode)) {
            return registerByServiceCode.get(serviceCode);
        }

        String registerId = registerIdFromIdentity(uniqueIdAfterProcess);
        if (!registerId.isBlank() && registerById.containsKey(registerId)) {
            return registerById.get(registerId);
        }
        return null;
    }

    private Map<String, Object> buildMappedRow(RegisterData register,
                                               Map<String, Object> rawData,
                                               Long syncedDeenrollmentDate,
                                               String campaignStartDate,
                                               String campaignEndDate) {
        String deEnrollmentDate = firstNonBlank(
                formatEpochIfPresent(syncedDeenrollmentDate),
                stringValue(rawData.get(DEENROLLMENT_DATE_COLUMN)),
                campaignEndDate
        );

        Map<String, Object> row = new LinkedHashMap<>();
        row.put(REGISTER_CODE_COLUMN, register.serviceCode);
        row.put(REGISTER_NAME_COLUMN, register.name);
        row.put(REGISTER_UUID_COLUMN, register.id);
        row.put(USER_NAME_COLUMN, stringValue(rawData.get(USER_NAME_COLUMN)));
        row.put(WORKER_ID_COLUMN, stringValue(rawData.get(WORKER_ID_COLUMN)));
        row.put(ROLE_COLUMN, extractRole(rawData));
        row.put(BOUNDARY_COLUMN, firstNonBlank(
                stringValue(rawData.get(BOUNDARY_COLUMN)),
                stringValue(rawData.get(BOUNDARY_CODE_MANDATORY_COLUMN)),
                stringValue(rawData.get(BOUNDARY_CODE_COLUMN))
        ));
        row.put(ENROLLMENT_DATE_COLUMN, firstNonBlank(
                stringValue(rawData.get(ENROLLMENT_DATE_COLUMN)),
                campaignStartDate
        ));
        row.put(DEENROLLMENT_DATE_COLUMN, deEnrollmentDate);
        return row;
    }

    private Map<String, Object> buildRegisterOnlyRow(RegisterData register,
                                                     String campaignStartDate,
                                                     String campaignEndDate) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(REGISTER_CODE_COLUMN, register.serviceCode);
        row.put(REGISTER_NAME_COLUMN, register.name);
        row.put(REGISTER_UUID_COLUMN, register.id);
        row.put(USER_NAME_COLUMN, "");
        row.put(WORKER_ID_COLUMN, "");
        row.put(ROLE_COLUMN, "");
        row.put(BOUNDARY_COLUMN, "");
        row.put(ENROLLMENT_DATE_COLUMN, campaignStartDate);
        row.put(DEENROLLMENT_DATE_COLUMN, campaignEndDate);
        return row;
    }

    private Map<String, Object> mergeRows(Map<String, Object> existing, Map<String, Object> incoming) {
        Map<String, Object> merged = new LinkedHashMap<>(existing);
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            String key = entry.getKey();
            String incomingValue = stringValue(entry.getValue());
            if (ROLE_COLUMN.equals(key)) {
                String mergedRole = mergeRoles(stringValue(existing.get(ROLE_COLUMN)), incomingValue);
                merged.put(ROLE_COLUMN, mergedRole);
                continue;
            }
            merged.put(key, firstNonBlank(stringValue(existing.get(key)), incomingValue));
        }
        return merged;
    }

    private String mergeRoles(String existing, String incoming) {
        Set<String> seen = new HashSet<>();
        List<String> roles = new ArrayList<>();
        for (String role : splitRoles(existing)) {
            String normalized = role.toUpperCase(Locale.ROOT);
            if (seen.add(normalized)) roles.add(role);
        }
        for (String role : splitRoles(incoming)) {
            String normalized = role.toUpperCase(Locale.ROOT);
            if (seen.add(normalized)) roles.add(role);
        }
        return String.join(", ", roles);
    }

    private List<String> splitRoles(String roleValue) {
        if (roleValue == null || roleValue.isBlank()) return Collections.emptyList();
        List<String> roles = new ArrayList<>();
        for (String part : roleValue.split(",")) {
            String role = part.trim();
            if (!role.isBlank()) roles.add(role);
        }
        return roles;
    }

    private String extractRole(Map<String, Object> rawData) {
        String baseRole = stringValue(rawData.get(ROLE_COLUMN));
        if (!baseRole.isBlank()) {
            return mergeRoles("", baseRole);
        }

        StringBuilder roleBuilder = new StringBuilder();
        for (int i = 1; i <= MAX_ROLE_COLUMNS; i++) {
            String role = stringValue(rawData.get("HCM_ADMIN_CONSOLE_USER_ROLE_MULTISELECT_" + i));
            if (role.isBlank()) continue;
            if (roleBuilder.length() > 0) roleBuilder.append(", ");
            roleBuilder.append(role);
        }
        return mergeRoles("", roleBuilder.toString());
    }

    private String personIdentity(Map<String, Object> rawData, String fallbackUniqueIdentifier) {
        return firstNonBlank(
                stringValue(rawData.get(WORKER_ID_COLUMN)),
                stringValue(rawData.get(USERNAME_COLUMN)),
                stringValue(rawData.get(USER_NAME_COLUMN)),
                fallbackUniqueIdentifier,
                "__unknown__"
        );
    }

    private String registerIdFromIdentity(String uniqueIdAfterProcess) {
        if (uniqueIdAfterProcess == null || uniqueIdAfterProcess.isBlank()) return "";
        int separator = uniqueIdAfterProcess.indexOf('_');
        if (separator <= 0) return "";
        return uniqueIdAfterProcess.substring(0, separator).trim();
    }

    private boolean isDeleted(Map<String, Object> row) {
        Object value = row.get("isDeleted");
        if (value instanceof Boolean) return (Boolean) value;
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private Long getLongValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        String asText = String.valueOf(value).trim();
        if (asText.isBlank()) return null;
        try {
            return Long.parseLong(asText);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int compareByColumn(Map<String, Object> left, Map<String, Object> right, String... columns) {
        for (String column : columns) {
            int compare = stringValue(left.get(column)).compareTo(stringValue(right.get(column)));
            if (compare != 0) return compare;
        }
        return 0;
    }

    private String formatEpochIfPresent(Long epochMillis) {
        if (epochMillis == null) return "";
        LocalDate date = Instant.ofEpochMilli(epochMillis)
                .atZone(config.getServerZoneId())
                .toLocalDate();
        return date.format(SHEET_DATE_FORMAT);
    }

    private String resolveCampaignId(GenerateResource generateResource) {
        if (ProcessingConstants.REFERENCE_TYPE_CAMPAIGN.equalsIgnoreCase(generateResource.getReferenceType())) {
            String referenceId = stringValue(generateResource.getReferenceId());
            if (!referenceId.isBlank()) return referenceId;
        }

        Map<String, Object> additionalDetails = generateResource.getAdditionalDetails();
        if (additionalDetails != null) {
            String campaignId = stringValue(additionalDetails.get(ProcessingConstants.ADDITIONAL_DETAILS_CAMPAIGN_ID));
            if (!campaignId.isBlank()) return campaignId;
        }

        exceptionHandler.throwCustomException(
                ErrorConstants.MISSING_REQUIRED_FIELD,
                ErrorConstants.MISSING_REQUIRED_FIELD_MESSAGE.replace("{0}", "campaignId"),
                new RuntimeException("campaignId is required in referenceId or additionalDetails"));
        return "";
    }

    private List<ColumnDef> fetchSchemaColumnDefs(String schemaName, String tenantId, RequestInfo requestInfo) {
        if (schemaName == null || schemaName.isBlank()) {
            exceptionHandler.throwCustomException(ErrorConstants.MISSING_REQUIRED_FIELD,
                    ErrorConstants.MISSING_REQUIRED_FIELD_MESSAGE.replace("{0}", "schemaName"),
                    new RuntimeException("schemaName missing in generation config"));
        }

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
            throw ce;
        } catch (Exception e) {
            exceptionHandler.throwCustomException(ErrorConstants.SCHEMA_CONVERSION_ERROR,
                    ErrorConstants.SCHEMA_CONVERSION_ERROR_MESSAGE, e);
            return Collections.emptyList();
        }
    }

    private Map<String, IndividualProfile> fetchIndividualProfiles(
            String tenantId,
            RequestInfo requestInfo,
            Set<String> personIds
    ) {
        Map<String, IndividualProfile> profiles = new HashMap<>();
        if (personIds == null || personIds.isEmpty()) return profiles;

        List<String> ids = new ArrayList<>(personIds);
        for (int i = 0; i < ids.size(); i += INDIVIDUAL_SEARCH_BATCH_SIZE) {
            List<String> batch = ids.subList(i, Math.min(i + INDIVIDUAL_SEARCH_BATCH_SIZE, ids.size()));
            StringBuilder url = new StringBuilder(config.getHealthIndividualHost())
                    .append(config.getHealthIndividualSearchPath())
                    .append("?tenantId=").append(tenantId)
                    .append("&limit=").append(batch.size() + 5)
                    .append("&offset=0");

            Map<String, Object> body = new HashMap<>();
            body.put("RequestInfo", requestInfo);
            body.put("Individual", Map.of("id", batch));

            Map<String, Object> response;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = serviceRequestRepository.fetchResult(url, body, Map.class);
                response = result == null ? Collections.emptyMap() : result;
            } catch (RuntimeException ex) {
                log.warn("Unable to enrich fallback rows from individual service for ids {}: {}", batch.size(), ex.getMessage());
                continue;
            }

            for (Map<String, Object> individual : asMapList(response.get("Individual"))) {
                String individualId = stringValue(individual.get("id"));
                if (individualId.isBlank()) continue;
                profiles.put(individualId, new IndividualProfile(
                        displayNameFromIndividual(individual),
                        firstNonBlank(
                                stringValue(asMap(individual.get("userDetails")) != null
                                        ? asMap(individual.get("userDetails")).get("username")
                                        : null),
                                stringValue(individual.get("username"))
                        )
                ));
            }
        }

        return profiles;
    }

    private String displayNameFromIndividual(Map<String, Object> individual) {
        Map<String, Object> name = asMap(individual.get("name"));
        List<String> parts = new ArrayList<>();
        String givenName = stringValue(name != null ? name.get("givenName") : null);
        String otherNames = stringValue(name != null ? name.get("otherNames") : null);
        String familyName = stringValue(name != null ? name.get("familyName") : null);
        if (!givenName.isBlank()) parts.add(givenName);
        if (!otherNames.isBlank()) parts.add(otherNames);
        if (!familyName.isBlank()) parts.add(familyName);
        String composed = String.join(" ", parts);
        return firstNonBlank(composed, stringValue(individual.get("username")));
    }

    private String displayNameFromStaff(Map<String, Object> staff) {
        Map<String, Object> details = asMap(staff.get("additionalDetails"));
        return firstNonBlank(
                stringValue(details != null ? details.get("staffName") : null),
                stringValue(details != null ? details.get("ownerName") : null)
        );
    }

    private String normalizeRoleFromStaffType(Object staffType) {
        String normalized = stringValue(staffType).toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return "";
        if (STAFF_TYPE_APPROVER.equals(normalized)) return ROLE_APPROVER;
        if (STAFF_TYPE_OWNER.equals(normalized)) return ROLE_MARKER;
        return normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value == null) continue;
            String trimmed = value.trim();
            if (!trimmed.isBlank()) return trimmed;
        }
        return "";
    }

    private String stringValue(Object value) {
        if (value == null) return "";
        return String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object obj) {
        if (!(obj instanceof List)) return Collections.emptyList();
        List<?> items = (List<?>) obj;
        List<Map<String, Object>> maps = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map) {
                maps.add((Map<String, Object>) item);
            }
        }
        return maps;
    }

    static class RegisterData {
        final String id;
        final String serviceCode;
        final String name;
        final String localityCode;
        final List<Map<String, Object>> attendees;
        final List<Map<String, Object>> staff;

        RegisterData(String id, String serviceCode, String name) {
            this(id, serviceCode, name, "", Collections.emptyList(), Collections.emptyList());
        }

        RegisterData(String id,
                     String serviceCode,
                     String name,
                     String localityCode,
                     List<Map<String, Object>> attendees,
                     List<Map<String, Object>> staff) {
            this.id = id;
            this.serviceCode = serviceCode;
            this.name = name;
            this.localityCode = localityCode;
            this.attendees = attendees != null ? attendees : Collections.emptyList();
            this.staff = staff != null ? staff : Collections.emptyList();
        }

        String identity() {
            return id != null && !id.isBlank() ? id : serviceCode;
        }
    }

    static class IndividualProfile {
        final String displayName;
        final String username;

        IndividualProfile(String displayName, String username) {
            this.displayName = displayName;
            this.username = username;
        }
    }
}
