package org.egov.excelingestion.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
@Getter
@Setter
public class ExcelIngestionConfig {

    @Value("${egov.boundary.host}")
    private String boundaryHost;

    @Value("${egov.hierarchy.search.url}")
    private String hierarchySearchPath;

    @Value("${egov.boundary.relationship.search.url}")
    private String relationshipSearchPath;

    @Value("${egov.filestore.host}")
    private String filestoreHost;

    @Value("${egov.filestore.upload.endpoint}")
    private String filestoreUploadEndpoint;

    @Value("${egov.filestore.url.endpoint}")
    private String filestoreUrlEndpoint;

    @Value("${egov.mdms.host}")
    private String mdmsHost;

    @Value("${egov.mdms.search.path}")
    private String mdmsSearchPath;

    @Value("${egov.campaign.host}")
    private String campaignHost;

    @Value("${egov.campaign.data.search.path}")
    private String campaignDataSearchPath;

    @Value("${excel.row.limit:5000}")
    private int excelRowLimit;

    @Value("${excel.max.process.row.limit:100000}")
    private int maxProcessRowLimit;

    @Value("${excel.sheet.name.max.length:64}")
    private int sheetNameMaxLength;

    @Value("${default.locale:en_IN}")
    private String defaultLocale;

    @Value("${egov.excel.sheet.password:passwordhere}")
    private String excelSheetPassword;

    @Value("${egov.excel.sheet.zoom:60}")
    private int excelSheetZoom;

    @Value("${egov.excel.validation.error.color:#ff0000}")
    private String validationErrorColor;

    @Value("${egov.health.individual.host}")
    private String healthIndividualHost;

    @Value("${egov.health.individual.search.path}")
    private String healthIndividualSearchPath;

    @Value("${egov.facility.host}")
    private String facilityHost;

    @Value("${egov.facility.search.path}")
    private String facilitySearchPath;

    @Value("${egov.attendance.host}")
    private String attendanceHost;

    @Value("${egov.attendance.register.search.path}")
    private String attendanceRegisterSearchPath;

    @Value("${egov.attendance.attendee.search.path}")
    private String attendanceAttendeeSearchPath;

    @Value("${egov.attendance.staff.search.path}")
    private String attendanceStaffSearchPath;

    @Value("${egov.attendance.attendee.search.page.size:500}")
    private int attendanceAttendeeSearchPageSize;

    @Value("${egov.attendance.staff.search.page.size:100}")
    private int attendanceStaffSearchPageSize;

    @Value("${egov.attendance.register.search.batch.size:100}")
    private int attendanceRegisterSearchBatchSize;

    @Value("${egov.attendance.register.search.parallel.calls:5}")
    private int attendanceRegisterSearchParallelCalls;

    // Bounded concurrency for the user existence searches (phone/username) in UserValidationProcessor.
    // At large row counts these run as many 50-id batches; firing them with bounded parallelism instead
    // of sequentially is the dominant scale win for unified-console validation.
    @Value("${egov.excel.user.search.parallel.calls:20}")
    private int userSearchParallelCalls;

    // Number of phone/username ids per individual-search call. Fewer, larger batches mean fewer HTTP
    // round-trips. Default 100 (verified working in-app; ~halves the round-trips vs 50). The individual
    // search now paginates results, so a batch can never silently truncate matches; the only ceiling is
    // the individual-search API's per-query id-list cap (100 confirmed safe; raise with care).
    @Value("${egov.excel.user.search.batch.size:100}")
    private int userSearchBatchSize;

    @Value("${egov.hrms.host}")
    private String hrmsHost;

    @Value("${egov.hrms.employee.search.path}")
    private String hrmsEmployeeSearchPath;

    @Value("${egov.hrms.employee.search.parallel.calls:100}")
    private int hrmsEmployeeSearchParallelCalls;

    @Value("${egov.worker.registry.host}")
    private String workerRegistryHost;

    @Value("${egov.worker.registry.search.path}")
    private String workerRegistrySearchPath;

    @Value("${egov.worker.registry.search.batch.size:100}")
    private int workerRegistrySearchBatchSize;

    @Value("${app.timezone:UTC}")
    private String serverTimezone;

    // Master kill switch for the unprotected join-mode immutability feature (default ON). ANDed with
    // ProcessingConstants.isJoinModeType(type) at both gates, so even when ON the feature acts ONLY for
    // the join-mode template families (unified-console / attendanceRegister / attendanceRegisterAttendee),
    // never for other types.
    // true  -> for those families: generation produces unprotected, row-id-stamped templates AND uploads
    //          reconstruct pre-filled values from the original generated baseline (server-authoritative).
    // false -> full revert: protected templates + uploads processed as before (no reconstruction).
    @Value("${egov.excel.immutable.enforce:true}")
    private boolean immutableEnforce;

    // Server-side guard for boundary SELECTION-NAME columns ({hierarchyType}_<LEVEL>). The Excel dropdown is
    // client-side only and bypassable, so on upload we re-check every selection value against the campaign
    // hierarchy and fail the row if it is not a real boundary name. Kill switch in case of an edge case.
    @Value("${egov.excel.boundary.selection.name.validation:true}")
    private boolean validateBoundarySelectionNames;

    // "Exact file" guard: at generation we embed an HMAC over the generationId; on upload we require a
    // matching signature. The generationId itself is NOT secret (it rides in API responses / URLs / logs),
    // so without this anyone who learns it could stamp a hand-built file and pass identity. The signature
    // is only ever written into the genuine downloaded file, so it proves possession of THAT file.
    // The secret MUST be overridden per-environment with a strong random value, otherwise the signature is
    // forgeable (the default below is a placeholder, like the sheet password).
    @Value("${egov.excel.immutable.signing.secret:egov-excel-immutable-default-secret-change-me}")
    private String immutableSigningSecret;

    @Value("${egov.excel.immutable.signature.enforce:true}")
    private boolean immutableSignatureEnforce;

    private String defaultHeaderColor = "#93c47d";

    public ZoneId getServerZoneId() {
        return ZoneId.of(serverTimezone);
    }

    public String getHierarchySearchUrl() {
        return boundaryHost + hierarchySearchPath;
    }

    public String getRelationshipSearchUrl() {
        return boundaryHost + relationshipSearchPath;
    }

    public String getMdmsSearchUrl() {
        return mdmsHost + mdmsSearchPath;
    }

    public String getCampaignDataSearchUrl() {
        return campaignHost + campaignDataSearchPath;
    }

    public String getFacilitySearchUrl() {
        return facilityHost + facilitySearchPath;
    }

    public String getAttendanceRegisterSearchUrl() {
        return attendanceHost + attendanceRegisterSearchPath;
    }

    public String getAttendanceAttendeeSearchUrl() {
        return attendanceHost + attendanceAttendeeSearchPath;
    }

    public String getAttendanceStaffSearchUrl() {
        return attendanceHost + attendanceStaffSearchPath;
    }

    public int getAttendanceAttendeeSearchPageSize() {
        return attendanceAttendeeSearchPageSize;
    }

    public int getAttendanceStaffSearchPageSize() {
        return attendanceStaffSearchPageSize;
    }

    public String getWorkerRegistrySearchUrl() {
        return workerRegistryHost + workerRegistrySearchPath;
    }

    public String getHrmsEmployeeSearchUrl() {
        return hrmsHost + hrmsEmployeeSearchPath;
    }
}