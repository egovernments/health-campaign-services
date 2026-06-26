package org.egov.referralmanagement.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.DownsyncGenerationJobRepository;
import org.egov.referralmanagement.service.DownsyncS3Service.S3Result;
import org.egov.referralmanagement.web.models.LocalityDownsyncCriteria;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.namedparam.ParsedSql;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

@Service
@Slf4j
public class DownsyncFileGenService {

    public static final List<String> REGISTRY_FILE_TYPES = List.of("HH_MEMBERS", "INDIVIDUALS");
    public static final List<String> PROJECT_FILE_TYPES  = List.of("BENE_AE_REF", "TASKS");

    private static final DateTimeFormatter DOB_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── SQL templates ─────────────────────────────────────────────────────────

    private static final String HH_QUERY =
            "SELECT * FROM {schema}.household_address_mv " +
            "WHERE localitycode = :locality AND isdeleted = false";

    private static final String HH_MEMBER_QUERY =
            "SELECT * FROM {schema}.HOUSEHOLD_MEMBER " +
            "WHERE householdClientReferenceId IN " +
            "  (SELECT clientReferenceId FROM {schema}.household_address_mv " +
            "   WHERE localitycode = :locality AND isdeleted = false) " +
            "AND isDeleted = false";

    private static final String LOCALITY_BENE_SUBQUERY =
            "SELECT pb.clientReferenceId FROM {schema}.PROJECT_BENEFICIARY pb " +
            "WHERE pb.projectId = :projectId AND pb.isDeleted = false " +
            "AND pb.beneficiaryClientReferenceId IN (" +
            "  SELECT clientReferenceId FROM {schema}.household_address_mv " +
            "  WHERE localitycode = :locality AND isdeleted = false " +
            "  UNION " +
            "  SELECT hm.individualClientReferenceId " +
            "  FROM {schema}.HOUSEHOLD_MEMBER hm " +
            "  WHERE hm.isDeleted = false " +
            "  AND hm.householdClientReferenceId IN (" +
            "    SELECT clientReferenceId FROM {schema}.household_address_mv " +
            "    WHERE localitycode = :locality AND isdeleted = false" +
            "  )" +
            ")";

    private static final String INDIVIDUAL_QUERY =
            "SELECT ind.*," +
            "  addr_agg.addresses_json," +
            "  ident_agg.identifiers_json," +
            "  skill_agg.skills_json" +
            " FROM {schema}.INDIVIDUAL ind" +
            " LEFT JOIN (" +
            "   SELECT ia.individualId," +
            "     json_agg(json_build_object(" +
            "       'id', ia.addressId," +
            "       'clientReferenceId', a.clientReferenceId," +
            "       'individualId', ia.individualId," +
            "       'tenantId', a.tenantId," +
            "       'doorNo', a.doorNo," +
            "       'latitude', a.latitude," +
            "       'longitude', a.longitude," +
            "       'locationAccuracy', a.locationAccuracy," +
            "       'type', ia.type," +
            "       'addressLine1', a.addressLine1," +
            "       'addressLine2', a.addressLine2," +
            "       'landmark', a.landmark," +
            "       'city', a.city," +
            "       'pincode', a.pincode," +
            "       'buildingName', a.buildingName," +
            "       'street', a.street," +
            "       'locality', CASE WHEN a.localityCode IS NOT NULL" +
            "                        THEN json_build_object('code', a.localityCode) ELSE NULL END," +
            "       'isDeleted', ia.isDeleted," +
            "       'auditDetails', json_build_object(" +
            "         'createdBy', ia.createdBy, 'createdTime', ia.createdTime," +
            "         'lastModifiedBy', ia.lastModifiedBy, 'lastModifiedTime', ia.lastModifiedTime)" +
            "     )) AS addresses_json" +
            "   FROM {schema}.INDIVIDUAL_ADDRESS ia" +
            "   JOIN {schema}.ADDRESS a ON a.id = ia.addressId" +
            "   WHERE ia.isDeleted = false" +
            "   GROUP BY ia.individualId" +
            " ) addr_agg ON addr_agg.individualId = ind.id" +
            " LEFT JOIN (" +
            "   SELECT ii.individualId," +
            "     json_agg(json_build_object(" +
            "       'id', ii.id," +
            "       'clientReferenceId', ii.clientReferenceId," +
            "       'individualId', ii.individualId," +
            "       'individualClientReferenceId', ii.individualClientReferenceId," +
            "       'identifierType', ii.identifierType," +
            "       'identifierId', ii.identifierId," +
            "       'isDeleted', ii.isDeleted," +
            "       'auditDetails', json_build_object(" +
            "         'createdBy', ii.createdBy, 'createdTime', ii.createdTime," +
            "         'lastModifiedBy', ii.lastModifiedBy, 'lastModifiedTime', ii.lastModifiedTime)" +
            "     )) AS identifiers_json" +
            "   FROM {schema}.INDIVIDUAL_IDENTIFIER ii" +
            "   WHERE ii.isDeleted = false" +
            "   GROUP BY ii.individualId" +
            " ) ident_agg ON ident_agg.individualId = ind.id" +
            " LEFT JOIN (" +
            "   SELECT sk.individualId," +
            "     json_agg(json_build_object(" +
            "       'id', sk.id," +
            "       'clientReferenceId', sk.clientReferenceId," +
            "       'individualId', sk.individualId," +
            "       'type', sk.type," +
            "       'level', sk.level," +
            "       'experience', sk.experience," +
            "       'isDeleted', sk.isDeleted," +
            "       'auditDetails', json_build_object(" +
            "         'createdBy', sk.createdBy, 'createdTime', sk.createdTime," +
            "         'lastModifiedBy', sk.lastModifiedBy, 'lastModifiedTime', sk.lastModifiedTime)" +
            "     )) AS skills_json" +
            "   FROM {schema}.INDIVIDUAL_SKILL sk" +
            "   WHERE sk.isDeleted = false" +
            "   GROUP BY sk.individualId" +
            " ) skill_agg ON skill_agg.individualId = ind.id" +
            " WHERE ind.clientReferenceId IN (" +
            "  SELECT hm.individualClientReferenceId" +
            "  FROM {schema}.HOUSEHOLD_MEMBER hm" +
            "  WHERE hm.isDeleted = false" +
            "  AND hm.householdClientReferenceId IN (" +
            "    SELECT clientReferenceId FROM {schema}.household_address_mv" +
            "    WHERE localitycode = :locality AND isdeleted = false" +
            "  )" +
            ") AND ind.isDeleted = false";

    private static final String BENEFICIARY_QUERY =
            "SELECT pb.* FROM {schema}.PROJECT_BENEFICIARY pb " +
            "WHERE pb.projectId = :projectId AND pb.isDeleted = false " +
            "AND pb.beneficiaryClientReferenceId IN (" +
            "  SELECT clientReferenceId FROM {schema}.household_address_mv " +
            "  WHERE localitycode = :locality AND isdeleted = false " +
            "  UNION " +
            "  SELECT hm.individualClientReferenceId " +
            "  FROM {schema}.HOUSEHOLD_MEMBER hm " +
            "  WHERE hm.isDeleted = false " +
            "  AND hm.householdClientReferenceId IN (" +
            "    SELECT clientReferenceId FROM {schema}.household_address_mv " +
            "    WHERE localitycode = :locality AND isdeleted = false" +
            "  )" +
            ")";

    private static final String SIDE_EFFECT_QUERY =
            "SELECT se.* FROM {schema}.SIDE_EFFECT se " +
            "WHERE se.isDeleted = false " +
            "AND se.projectBeneficiaryClientReferenceId IN (" + LOCALITY_BENE_SUBQUERY + ")";

    private static final String REFERRAL_QUERY =
            "SELECT r.* FROM {schema}.REFERRAL r " +
            "WHERE r.projectid = :projectId AND r.isDeleted = false " +
            "AND r.projectBeneficiaryClientReferenceId IN (" + LOCALITY_BENE_SUBQUERY + ")";

    private static final String HF_REFERRAL_QUERY =
            "SELECT * FROM {schema}.HF_REFERRAL " +
            "WHERE projectid = :projectId AND localitycode = :locality AND isdeleted = false";

    private static final String TASK_QUERY =
            "SELECT pt.*," +
            "  CASE WHEN a.id IS NOT NULL THEN json_build_object(" +
            "    'id', a.id," +
            "    'clientReferenceId', a.clientReferenceId," +
            "    'tenantId', a.tenantId," +
            "    'doorNo', a.doorNo," +
            "    'latitude', a.latitude," +
            "    'longitude', a.longitude," +
            "    'locationAccuracy', a.locationAccuracy," +
            "    'type', a.type," +
            "    'addressLine1', a.addressLine1," +
            "    'addressLine2', a.addressLine2," +
            "    'landmark', a.landmark," +
            "    'city', a.city," +
            "    'pincode', a.pincode," +
            "    'buildingName', a.buildingName," +
            "    'street', a.street," +
            "    'locality', CASE WHEN a.localityCode IS NOT NULL" +
            "                     THEN json_build_object('code', a.localityCode) ELSE NULL END" +
            "  ) END AS address_json," +
            "  res_agg.resources_json" +
            " FROM {schema}.PROJECT_TASK pt" +
            " LEFT JOIN {schema}.ADDRESS a ON a.id = pt.addressId" +
            " LEFT JOIN (" +
            "   SELECT tr.taskId," +
            "     json_agg(json_build_object(" +
            "       'id', tr.id," +
            "       'tenantId', tr.tenantId," +
            "       'clientReferenceId', tr.clientReferenceId," +
            "       'taskId', tr.taskId," +
            "       'productVariantId', tr.productVariantId," +
            "       'quantity', tr.quantity," +
            "       'isDelivered', tr.isDelivered," +
            "       'deliveryComment', tr.reasonIfNotDelivered," +
            "       'isDeleted', tr.isDeleted," +
            "       'additionalFields', tr.additionalDetails," +
            "       'auditDetails', json_build_object(" +
            "         'createdBy', tr.createdBy, 'createdTime', tr.createdTime," +
            "         'lastModifiedBy', tr.lastModifiedBy, 'lastModifiedTime', tr.lastModifiedTime)" +
            "     )) AS resources_json" +
            "   FROM {schema}.TASK_RESOURCE tr" +
            "   JOIN {schema}.PROJECT_TASK pt2 ON pt2.id = tr.taskId" +
            "     AND pt2.projectId = :projectId AND pt2.isDeleted = false" +
            "   WHERE tr.isDeleted = false" +
            "   GROUP BY tr.taskId" +
            " ) res_agg ON res_agg.taskId = pt.id" +
            " WHERE pt.projectId = :projectId AND pt.isDeleted = false" +
            " AND pt.projectBeneficiaryClientReferenceId IN (" + LOCALITY_BENE_SUBQUERY + ")";

    @Autowired private NamedParameterJdbcTemplate namedJdbcTemplate;
    @Autowired private MultiStateInstanceUtil multiStateInstanceUtil;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ReferralManagementConfiguration config;
    @Autowired @Qualifier("objectMapper") private ObjectMapper objectMapper;
    @Autowired private DownsyncGenerationJobRepository jobRepository;
    @Autowired private DownsyncS3Service s3Service;
    @Autowired private DataSource dataSource;

    /** Connections reserved outside the wardPool. Covers:
     *   • household_address_mv refresh (1)
     *   • Spring Boot DataSourceHealthIndicator probe (1)
     *   • worker status writes — updateFileStarted / Completed / claim — that run
     *     in parallel with active workers (~1)
     *   • other in-flight API calls — /jobs/_search, conflict-gate checks,
     *     /beneficiary-downsync (~1)
     *
     *  Sized to keep the pool comfortably ahead of demand and avoid the
     *  "Connection is not available" error path even under load. */
    private static final int RESERVED_CONNECTIONS = 4;

    private RestTemplate restTemplate;
    private static final int CURSOR_FETCH_SIZE = 1000;

    private static final List<String> IND_COLS = List.of(
            "id", "clientReferenceId", "tenantId", "individualId", "userId", "userUuid",
            "givenName", "familyName", "otherNames", "dateOfBirth",
            "gender", "bloodGroup", "mobileNumber", "altContactNumber", "email",
            "fatherName", "husbandName", "relationship", "photo",
            "isSystemUser", "isSystemUserActive", "rowVersion", "isDeleted", "additionalDetails",
            "addresses_json", "identifiers_json", "skills_json",
            "createdBy", "createdTime", "lastModifiedBy", "lastModifiedTime",
            "clientCreatedBy", "clientCreatedTime", "clientLastModifiedBy", "clientLastModifiedTime"
    );

    private ExecutorService wardPool;
    private TransactionTemplate readOnlyTx;

    @PostConstruct
    public void init() {
        validateDbPoolSize();
        wardPool   = Executors.newFixedThreadPool(config.getWardPoolSize());
        readOnlyTx = new TransactionTemplate(txManager);
        readOnlyTx.setReadOnly(true);
        SimpleClientHttpRequestFactory httpFactory = new SimpleClientHttpRequestFactory();
        httpFactory.setConnectTimeout(5_000);
        httpFactory.setReadTimeout(30_000);
        restTemplate = new RestTemplate(httpFactory);
    }

    /**
     * Verifies that the HikariCP pool is sized to support concurrent file generation.
     * <p>
     * Required floor: {@code wardPoolSize + RESERVED_CONNECTIONS}. Anything less guarantees
     * connection starvation under load — wards block waiting for a connection, the health
     * probe times out, and Kubernetes restarts the pod. Failing fast at startup is far
     * cheaper than diagnosing the resulting crash loop in production.
     * <p>
     * Can be bypassed via {@code egov.downsync.pool.check.enabled=false} for local dev or
     * intentionally tiny deployments. Not recommended for production.
     */
    private void validateDbPoolSize() {
        if (!config.isPoolCheckEnabled()) {
            log.warn("Downsync DB pool size check is DISABLED (egov.downsync.pool.check.enabled=false). " +
                     "Skipping the safety guard.");
            return;
        }
        if (!(dataSource instanceof HikariDataSource hds)) {
            log.warn("DataSource is not a HikariDataSource (got {}). Skipping pool size check.",
                     dataSource.getClass().getName());
            return;
        }
        int hikariMax = hds.getMaximumPoolSize();
        int wardPoolSize = config.getWardPoolSize();
        int required = wardPoolSize + RESERVED_CONNECTIONS;
        if (hikariMax < required) {
            throw new IllegalStateException(String.format(
                    "Downsync DB pool too small: spring.datasource.hikari.maximum-pool-size=%d, " +
                    "but wardPool=%d needs at least %d connections " +
                    "(wardPool + %d reserved for MV refresh, health probe, status writes, " +
                    "and concurrent API calls). " +
                    "Bump the pool to %d or higher, OR set egov.downsync.pool.check.enabled=false " +
                    "to bypass (not recommended for production).",
                    hikariMax, wardPoolSize, required, RESERVED_CONNECTIONS, required));
        }
        log.info("Downsync DB pool size check passed: hikariMax={}, required={}, wardPool={}, reserved={}",
                 hikariMax, required, wardPoolSize, RESERVED_CONNECTIONS);
    }

    @PreDestroy
    public void shutdown() {
        wardPool.shutdown();
    }

    // ── Registry generation (HH_MEMBERS + INDIVIDUALS, locality-scoped) ───────

    public void generateRegistry(List<LocalityDownsyncCriteria> localities, String jobId) {
        List<CompletableFuture<Void>> futures = localities.stream()
                .map(c -> CompletableFuture.runAsync(() -> generateRegistryLocality(c, jobId), wardPool))
                .toList();
        futures.forEach(CompletableFuture::join);
    }

    private void generateRegistryLocality(LocalityDownsyncCriteria criteria, String jobId) {
        String tid = criteria.getTenantId();
        String localityRowId = criteria.getLocalityRowId();
        jobRepository.updateLocalityStarted(tid, localityRowId, System.currentTimeMillis());
        try {
            List<DownsyncGenerationJobRepository.ResumableFile> resumable = filterResumable(
                    jobRepository.findResumableFileTypes(tid, localityRowId));
            if (resumable.isEmpty()) {
                jobRepository.updateLocalityCompleted(tid, localityRowId, "SUCCESS", null, System.currentTimeMillis());
                return;
            }

            int skipped = 0, failed = 0, run = 0;
            for (DownsyncGenerationJobRepository.ResumableFile rf : resumable) {
                String workingId = resolveWorkingId(tid, localityRowId, jobId, rf);
                if (workingId == null) continue;   // raced with another worker; latest is terminal
                run++;
                if (!criteria.isForceRefresh()) {
                    String skipReason = getFileSkipReason(criteria, rf.fileType());
                    if (skipReason != null) {
                        log.info("File SKIPPED — type={} locality={} reason={}",
                                rf.fileType(), criteria.getLocality(), skipReason);
                        jobRepository.updateFileCompleted(tid, workingId, "SKIPPED",
                                null, null, null, skipReason, System.currentTimeMillis());
                        skipped++;
                        continue;
                    }
                }
                if (!runRegistryFile(rf.fileType(), criteria, localityRowId, workingId).success()) failed++;
            }

            String localityStatus;
            if (run == 0)                            localityStatus = "SUCCESS";   // nothing to do; everything was already terminal
            else if (skipped == run)                 localityStatus = "SKIPPED";
            else if (failed == 0)                    localityStatus = "SUCCESS";
            else                                     localityStatus = "PARTIAL_SUCCESS";

            jobRepository.updateLocalityCompleted(tid, localityRowId, localityStatus, null, System.currentTimeMillis());
            log.info("Registry locality {} → {}", criteria.getLocality(), localityStatus);
        } catch (Exception e) {
            log.error("Registry locality {} FAILED: {}", criteria.getLocality(), e.getMessage());
            jobRepository.updateLocalityCompleted(tid, localityRowId, "FAILED",
                    truncate(e.getMessage()), System.currentTimeMillis());
        }
    }

    // ── Project generation (BENE_AE_REF + TASKS, always regenerated) ─────────

    public void generateProject(List<LocalityDownsyncCriteria> localities, String jobId) {
        List<CompletableFuture<Void>> futures = localities.stream()
                .map(c -> CompletableFuture.runAsync(() -> generateProjectLocality(c, jobId), wardPool))
                .toList();
        futures.forEach(CompletableFuture::join);
    }

    private void generateProjectLocality(LocalityDownsyncCriteria criteria, String jobId) {
        String tid = criteria.getTenantId();
        String localityRowId = criteria.getLocalityRowId();
        jobRepository.updateLocalityStarted(tid, localityRowId, System.currentTimeMillis());
        try {
            List<DownsyncGenerationJobRepository.ResumableFile> resumable = filterResumable(
                    jobRepository.findResumableFileTypes(tid, localityRowId));
            if (resumable.isEmpty()) {
                jobRepository.updateLocalityCompleted(tid, localityRowId, "SUCCESS", null, System.currentTimeMillis());
                return;
            }

            int skipped = 0, failed = 0, run = 0;
            for (DownsyncGenerationJobRepository.ResumableFile rf : resumable) {
                String workingId = resolveWorkingId(tid, localityRowId, jobId, rf);
                if (workingId == null) continue;
                run++;
                if (!criteria.isForceRefresh()) {
                    String skipReason = getFileSkipReason(criteria, rf.fileType());
                    if (skipReason != null) {
                        log.info("File SKIPPED — type={} locality={} reason={}",
                                rf.fileType(), criteria.getLocality(), skipReason);
                        jobRepository.updateFileCompleted(tid, workingId, "SKIPPED",
                                null, null, null, skipReason, System.currentTimeMillis());
                        skipped++;
                        continue;
                    }
                }
                if (!runProjectFile(rf.fileType(), criteria, localityRowId, workingId).success()) failed++;
            }

            String localityStatus;
            if (run == 0)                            localityStatus = "SUCCESS";
            else if (skipped == run)                 localityStatus = "SKIPPED";
            else if (failed == 0)                    localityStatus = "SUCCESS";
            else                                     localityStatus = "PARTIAL_SUCCESS";

            jobRepository.updateLocalityCompleted(tid, localityRowId, localityStatus, null, System.currentTimeMillis());
            log.info("Project locality {} → {}", criteria.getLocality(), localityStatus);
        } catch (Exception e) {
            log.error("Project locality {} FAILED: {}", criteria.getLocality(), e.getMessage());
            jobRepository.updateLocalityCompleted(tid, localityRowId, "FAILED",
                    truncate(e.getMessage()), System.currentTimeMillis());
        }
    }

    /** Drop terminal-state rows; only PENDING / IN_PROGRESS / FAILED need work. */
    private List<DownsyncGenerationJobRepository.ResumableFile> filterResumable(
            List<DownsyncGenerationJobRepository.ResumableFile> all) {
        return all.stream()
                .filter(rf -> {
                    String s = rf.status();
                    return "PENDING".equals(s) || "IN_PROGRESS".equals(s) || "FAILED".equals(s);
                })
                .toList();
    }

    /**
     * Resolve the row id we'll write to for this attempt.
     *
     * Decision table based on the latest attempt's status:
     *   FAILED      → INSERT a new attempt row. The FAILED row stays as history.
     *   PENDING     → CAS-claim the existing row in place. Returns null if claim lost
     *                 (another worker beat us — a parallel scan won the race).
     *   IN_PROGRESS → Another LIVE worker owns this row. Skip silently.
     *
     *                 Note: a CRASHED IN_PROGRESS row gets converted to FAILED by the
     *                 resume runner's sweepStaleFilesForJob before workers ever start,
     *                 so by the time we see IN_PROGRESS here, the only explanation is
     *                 "another live worker is processing it" — never "crashed". With
     *                 the strict PENDING-only CAS in claimFileAttempt, leaving the row
     *                 alone is the safe behavior.
     */
    private String resolveWorkingId(String tid, String localityRowId, String jobId,
                                     DownsyncGenerationJobRepository.ResumableFile rf) {
        switch (rf.status()) {
            case "FAILED" -> {
                String newId = UUID.randomUUID().toString();
                jobRepository.insertRetryAttempt(tid, newId, localityRowId, jobId,
                        rf.fileType(), System.currentTimeMillis());
                return newId;
            }
            case "PENDING" -> {
                boolean won = jobRepository.claimFileAttempt(tid, rf.id(), System.currentTimeMillis());
                if (!won) {
                    log.info("File attempt {} ({}) PENDING but claim lost to another worker; skipping",
                            rf.id(), rf.fileType());
                    return null;
                }
                return rf.id();
            }
            case "IN_PROGRESS" -> {
                log.info("File attempt {} ({}) IN_PROGRESS — another live worker owns it; skipping",
                        rf.id(), rf.fileType());
                return null;
            }
            default -> {
                // SUCCESS / SKIPPED — already filtered out by filterResumable, but defensive.
                log.warn("File attempt {} ({}) in unexpected status {} — skipping",
                        rf.id(), rf.fileType(), rf.status());
                return null;
            }
        }
    }

    // ── Staleness check ───────────────────────────────────────────────────────

    /**
     * Returns null if the file must be regenerated, or a skip reason string if it can be skipped.
     * Each file type is checked against its own data source(s).
     */
    public String getFileSkipReason(LocalityDownsyncCriteria c, String fileType) {
        String tenantId  = c.getTenantId();
        String locality  = c.getLocality();
        String projectId = c.getProjectId();

        Long lastEndTime = projectId == null
                ? jobRepository.findLatestFileEndTime(tenantId, locality, fileType)
                : jobRepository.findLatestProjectFileEndTime(tenantId, locality, projectId, fileType);
        if (lastEndTime == null) return null;

        Long maxModified = switch (fileType) {
            case "HH_MEMBERS"  -> maxOf(
                    jobRepository.findMaxHouseholdModifiedTime(tenantId, locality),
                    jobRepository.findMaxHhMemberModifiedTime(tenantId, locality));
            case "INDIVIDUALS" -> jobRepository.findMaxIndividualModifiedTime(tenantId, locality);
            case "BENE_AE_REF" -> maxOf(
                    jobRepository.findMaxBeneficiaryModifiedTime(tenantId, locality, projectId),
                    jobRepository.findMaxSideEffectModifiedTime(tenantId, locality, projectId),
                    jobRepository.findMaxReferralModifiedTime(tenantId, locality, projectId),
                    jobRepository.findMaxHfReferralModifiedTime(tenantId, locality, projectId));
            case "TASKS"       -> jobRepository.findMaxTaskModifiedTime(tenantId, locality, projectId);
            default            -> null;
        };

        if (maxModified == null) return "Skipped: no data found in locality";
        if (maxModified > lastEndTime) return null;
        return "Skipped: no data changes since last generation";
    }

    private Long maxOf(Long... values) {
        Long result = null;
        for (Long v : values) {
            if (v != null && (result == null || v > result)) result = v;
        }
        return result;
    }

    // ── File dispatchers ──────────────────────────────────────────────────────

    /** rowId here is the FILE row id (the working attempt) — already claimed/inserted upstream. */
    private FileResult runRegistryFile(String fileType, LocalityDownsyncCriteria c, String localityRowId, String fileRowId) {
        return switch (fileType) {
            case "HH_MEMBERS"  -> streamHhMembersFile(c, fileRowId);
            case "INDIVIDUALS" -> streamIndividualsFile(c, fileRowId);
            default -> throw new CustomException("UNKNOWN_REGISTRY_FILE_TYPE",
                    "Unrecognised registry file type '" + fileType + "'. Expected one of: HH_MEMBERS, INDIVIDUALS.");
        };
    }

    private FileResult runProjectFile(String fileType, LocalityDownsyncCriteria c, String localityRowId, String fileRowId) {
        return switch (fileType) {
            case "BENE_AE_REF" -> streamBeneAeRefFile(c, fileRowId);
            case "TASKS"       -> streamTasksFile(c, fileRowId);
            default -> throw new CustomException("UNKNOWN_PROJECT_FILE_TYPE",
                    "Unrecognised project file type '" + fileType + "'. Expected one of: BENE_AE_REF, TASKS.");
        };
    }

    // ── File streaming methods ────────────────────────────────────────────────
    //
    // The "IN_PROGRESS / startTime" stamp has already been written upstream by
    // resolveWorkingId() (via claimFileAttempt or insertRetryAttempt), so these
    // methods do their work and write only the terminal completion. updateFileCompleted
    // is a CAS on status='IN_PROGRESS' — so even if the row was finalized in parallel
    // by another writer, our write is a no-op and we still return our own FileResult.

    private FileResult streamHhMembersFile(LocalityDownsyncCriteria c, String fileRowId) {
        String tid = c.getTenantId();
        String key = s3RegistryKey(c, "hh_members");
        try {
            S3Result s3 = s3Service.streamToS3(key, gzip -> {
                long n = streamQuery(gzip, resolveSql(HH_QUERY, tid), localityParams(c), "HOUSEHOLD");
                return n + streamQuery(gzip, resolveSql(HH_MEMBER_QUERY, tid), localityParams(c), "HOUSEHOLD_MEMBER");
            });
            jobRepository.updateFileCompleted(tid, fileRowId, "SUCCESS",
                    s3.rowCount() > 0 ? key : null, s3.rowCount(), s3.fileSize(), null, System.currentTimeMillis());
            return new FileResult("HH_MEMBERS", true, s3.rowCount() > 0 ? key : null, s3.rowCount(), null);
        } catch (Exception e) {
            jobRepository.updateFileCompleted(tid, fileRowId, "FAILED",
                    null, null, null, truncate(e.getMessage()), System.currentTimeMillis());
            return new FileResult("HH_MEMBERS", false, null, 0, e.getMessage());
        }
    }

    private FileResult streamIndividualsFile(LocalityDownsyncCriteria c, String fileRowId) {
        String tid = c.getTenantId();
        String key = s3RegistryKey(c, "individuals");
        try {
            S3Result s3 = s3Service.streamToS3(key, gzip ->
                    streamIndividualQuery(gzip, resolveSql(INDIVIDUAL_QUERY, tid), localityParams(c)));
            jobRepository.updateFileCompleted(tid, fileRowId, "SUCCESS",
                    s3.rowCount() > 0 ? key : null, s3.rowCount(), s3.fileSize(), null, System.currentTimeMillis());
            return new FileResult("INDIVIDUALS", true, s3.rowCount() > 0 ? key : null, s3.rowCount(), null);
        } catch (Exception e) {
            jobRepository.updateFileCompleted(tid, fileRowId, "FAILED",
                    null, null, null, truncate(e.getMessage()), System.currentTimeMillis());
            return new FileResult("INDIVIDUALS", false, null, 0, e.getMessage());
        }
    }

    private FileResult streamBeneAeRefFile(LocalityDownsyncCriteria c, String fileRowId) {
        String tid = c.getTenantId();
        String key = s3ProjectKey(c, "bene_ae_ref");
        try {
            S3Result s3 = s3Service.streamToS3(key, gzip -> {
                long n = streamQuery(gzip, resolveSql(BENEFICIARY_QUERY, tid), projectLocalityParams(c), "PROJECT_BENEFICIARY");
                n += streamQuery(gzip, resolveSql(SIDE_EFFECT_QUERY, tid), projectLocalityParams(c), "SIDE_EFFECT");
                n += streamQuery(gzip, resolveSql(REFERRAL_QUERY, tid), projectLocalityParams(c), "REFERRAL");
                return n + streamQuery(gzip, resolveSql(HF_REFERRAL_QUERY, tid), projectLocalityParams(c), "HF_REFERRAL");
            });
            jobRepository.updateFileCompleted(tid, fileRowId, "SUCCESS",
                    s3.rowCount() > 0 ? key : null, s3.rowCount(), s3.fileSize(), null, System.currentTimeMillis());
            return new FileResult("BENE_AE_REF", true, s3.rowCount() > 0 ? key : null, s3.rowCount(), null);
        } catch (Exception e) {
            jobRepository.updateFileCompleted(tid, fileRowId, "FAILED",
                    null, null, null, truncate(e.getMessage()), System.currentTimeMillis());
            return new FileResult("BENE_AE_REF", false, null, 0, e.getMessage());
        }
    }

    private FileResult streamTasksFile(LocalityDownsyncCriteria c, String fileRowId) {
        String tid = c.getTenantId();
        String key = s3ProjectKey(c, "tasks");
        try {
            S3Result s3 = s3Service.streamToS3(key, gzip ->
                    streamQuery(gzip, resolveSql(TASK_QUERY, tid), projectLocalityParams(c), "PROJECT_TASK"));
            jobRepository.updateFileCompleted(tid, fileRowId, "SUCCESS",
                    s3.rowCount() > 0 ? key : null, s3.rowCount(), s3.fileSize(), null, System.currentTimeMillis());
            return new FileResult("TASKS", true, s3.rowCount() > 0 ? key : null, s3.rowCount(), null);
        } catch (Exception e) {
            jobRepository.updateFileCompleted(tid, fileRowId, "FAILED",
                    null, null, null, truncate(e.getMessage()), System.currentTimeMillis());
            return new FileResult("TASKS", false, null, 0, e.getMessage());
        }
    }

    // ── JDBC cursor streaming ─────────────────────────────────────────────────

    private long streamQuery(GZIPOutputStream gzip, String namedSql,
                             Map<String, Object> params, String typeTag) {
        ParsedSql parsed = NamedParameterUtils.parseSqlStatement(namedSql);
        SqlParameterSource paramSource = new MapSqlParameterSource(params);
        String positionalSql = NamedParameterUtils.substituteNamedParameters(parsed, paramSource);
        Object[] positionalParams = NamedParameterUtils.buildValueArray(parsed, paramSource, null);

        long[] count = {0};
        readOnlyTx.execute(status -> {
            namedJdbcTemplate.getJdbcTemplate().query(
                    con -> {
                        PreparedStatement ps = con.prepareStatement(positionalSql,
                                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                        ps.setFetchSize(CURSOR_FETCH_SIZE);
                        for (int i = 0; i < positionalParams.length; i++)
                            ps.setObject(i + 1, positionalParams[i]);
                        return ps;
                    },
                    (ResultSet rs) -> {
                        try {
                            JsonGenerator gen = objectMapper.getFactory().createGenerator(gzip);
                            gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
                            try {
                                while (rs.next()) {
                                    gen.writeStartObject();
                                    gen.writeStringField("_t", typeTag);
                                    writeTypedRow(gen, rs, typeTag);
                                    gen.writeEndObject();
                                    gen.writeRaw('\n');
                                    count[0]++;
                                }
                            } finally {
                                gen.flush();
                                gen.close();
                            }
                        } catch (IOException | SQLException e) {
                            throw new CustomException(typeTag + "_STREAM_ERROR",
                                    "Failed to stream entity type '" + typeTag + "' to NDJSON output. Cause: " + e.getMessage());
                        }
                        return null;
                    }
            );
            return null;
        });
        return count[0];
    }

    // ── Type dispatcher ───────────────────────────────────────────────────────

    private void writeTypedRow(JsonGenerator gen, ResultSet rs, String typeTag)
            throws IOException, SQLException {
        switch (typeTag) {
            case "HOUSEHOLD"          -> writeHousehold(gen, rs);
            case "HOUSEHOLD_MEMBER"   -> writeHouseholdMember(gen, rs);
            case "INDIVIDUAL"         -> writeIndividual(gen, rs);
            case "PROJECT_BENEFICIARY"-> writeProjectBeneficiary(gen, rs);
            case "SIDE_EFFECT"        -> writeSideEffect(gen, rs);
            case "REFERRAL"           -> writeReferral(gen, rs);
            case "HF_REFERRAL"        -> writeHfReferral(gen, rs);
            case "PROJECT_TASK"       -> writeProjectTask(gen, rs);
            default -> throw new CustomException("UNKNOWN_ENTITY_TYPE_TAG",
                    "No writer registered for entity type '" + typeTag + "'. " +
                    "Supported types: HOUSEHOLD, HOUSEHOLD_MEMBER, INDIVIDUAL, PROJECT_BENEFICIARY, " +
                    "SIDE_EFFECT, REFERRAL, HF_REFERRAL, PROJECT_TASK.");
        }
    }

    // ── Per-type writers ──────────────────────────────────────────────────────

    private void writeHousehold(JsonGenerator gen, ResultSet rs) throws IOException, SQLException {
        ws(gen, "id",              rs, "id");
        ws(gen, "clientReferenceId", rs, "clientReferenceId");
        ws(gen, "tenantId",        rs, "tenantId");
        wi(gen, "memberCount",     rs, "numberOfMembers");
        ws(gen, "householdType",   rs, "householdType");
        wi(gen, "rowVersion",      rs, "rowVersion");
        wb(gen, "isDeleted",       rs, "isDeleted");
        wjson(gen, "additionalFields", rs, "additionalDetails");
        wAudit(gen, rs);
        wClientAudit(gen, rs);

        String addrId = rs.getString("aid");
        if (addrId == null) {
            gen.writeNullField("address");
        } else {
            gen.writeFieldName("address");
            gen.writeStartObject();
            ws(gen, "id",               rs, "aid");
            ws(gen, "clientReferenceId", rs, "aclientreferenceid");
            ws(gen, "tenantId",         rs, "atenantid");
            ws(gen, "doorNo",           rs, "doorNo");
            wd(gen, "latitude",         rs, "latitude");
            wd(gen, "longitude",        rs, "longitude");
            wd(gen, "locationAccuracy", rs, "locationAccuracy");
            ws(gen, "type",             rs, "type");
            ws(gen, "addressLine1",     rs, "addressLine1");
            ws(gen, "addressLine2",     rs, "addressLine2");
            ws(gen, "landmark",         rs, "landmark");
            ws(gen, "city",             rs, "city");
            ws(gen, "pincode",          rs, "pinCode");
            ws(gen, "buildingName",     rs, "buildingName");
            ws(gen, "street",           rs, "street");
            writeLocality(gen, rs, "localityCode");
            gen.writeEndObject();
        }
    }

    private void writeHouseholdMember(JsonGenerator gen, ResultSet rs) throws IOException, SQLException {
        ws(gen, "id",                          rs, "id");
        ws(gen, "clientReferenceId",           rs, "clientReferenceId");
        ws(gen, "tenantId",                    rs, "tenantId");
        ws(gen, "householdId",                 rs, "householdId");
        ws(gen, "householdClientReferenceId",  rs, "householdClientReferenceId");
        ws(gen, "individualId",                rs, "individualId");
        ws(gen, "individualClientReferenceId", rs, "individualClientReferenceId");
        wb(gen, "isHeadOfHousehold",           rs, "isHeadOfHousehold");
        wi(gen, "rowVersion",                  rs, "rowVersion");
        wb(gen, "isDeleted",                   rs, "isDeleted");
        wjson(gen, "additionalFields",         rs, "additionalDetails");
        wAudit(gen, rs);
        wClientAudit(gen, rs);
    }

    private void writeIndividual(JsonGenerator gen, ResultSet rs) throws IOException, SQLException {
        ws(gen, "id",               rs, "id");
        ws(gen, "clientReferenceId", rs, "clientReferenceId");
        ws(gen, "tenantId",         rs, "tenantId");
        ws(gen, "individualId",     rs, "individualId");
        ws(gen, "userId",           rs, "userId");
        ws(gen, "userUuid",         rs, "userUuid");

        gen.writeFieldName("name");
        gen.writeStartObject();
        ws(gen, "givenName",   rs, "givenName");
        ws(gen, "familyName",  rs, "familyName");
        ws(gen, "otherNames",  rs, "otherNames");
        gen.writeEndObject();

        java.sql.Date dob = rs.getDate("dateOfBirth");
        if (dob == null) gen.writeNullField("dateOfBirth");
        else gen.writeStringField("dateOfBirth", dob.toLocalDate().format(DOB_FMT));

        ws(gen, "gender",            rs, "gender");
        ws(gen, "bloodGroup",        rs, "bloodGroup");
        ws(gen, "mobileNumber",      rs, "mobileNumber");
        ws(gen, "altContactNumber",  rs, "altContactNumber");
        ws(gen, "email",             rs, "email");
        ws(gen, "fatherName",        rs, "fatherName");
        ws(gen, "husbandName",       rs, "husbandName");
        ws(gen, "relationship",      rs, "relationship");
        ws(gen, "photo",             rs, "photo");
        wb(gen, "isSystemUser",      rs, "isSystemUser");
        wb(gen, "isSystemUserActive", rs, "isSystemUserActive");
        wi(gen, "rowVersion",        rs, "rowVersion");
        wb(gen, "isDeleted",         rs, "isDeleted");
        wjson(gen, "additionalFields", rs, "additionalDetails");

        wjson(gen, "address",      rs, "addresses_json");
        wjson(gen, "identifiers",  rs, "identifiers_json");
        wjson(gen, "skills",       rs, "skills_json");

        wAudit(gen, rs);
        wClientAudit(gen, rs);
    }

    private void writeProjectBeneficiary(JsonGenerator gen, ResultSet rs) throws IOException, SQLException {
        ws(gen, "id",                            rs, "id");
        ws(gen, "clientReferenceId",             rs, "clientReferenceId");
        ws(gen, "tenantId",                      rs, "tenantId");
        ws(gen, "projectId",                     rs, "projectId");
        ws(gen, "beneficiaryId",                 rs, "beneficiaryId");
        ws(gen, "beneficiaryClientReferenceId",  rs, "beneficiaryClientReferenceId");
        wl(gen, "dateOfRegistration",            rs, "dateOfRegistration");
        ws(gen, "tag",                           rs, "tag");
        wi(gen, "rowVersion",                    rs, "rowVersion");
        wb(gen, "isDeleted",                     rs, "isDeleted");
        wjson(gen, "additionalFields",           rs, "additionalDetails");
        wAudit(gen, rs);
        wClientAudit(gen, rs);
    }

    private void writeSideEffect(JsonGenerator gen, ResultSet rs) throws IOException, SQLException {
        ws(gen, "id",                                    rs, "id");
        ws(gen, "clientReferenceId",                     rs, "clientReferenceId");
        ws(gen, "tenantId",                              rs, "tenantId");
        ws(gen, "taskId",                                rs, "taskId");
        ws(gen, "taskClientReferenceId",                 rs, "taskClientReferenceId");
        ws(gen, "projectBeneficiaryId",                  rs, "projectBeneficiaryId");
        ws(gen, "projectBeneficiaryClientReferenceId",   rs, "projectBeneficiaryClientReferenceId");
        wjson(gen, "symptoms",                           rs, "symptoms");
        wi(gen, "rowVersion",                            rs, "rowVersion");
        wb(gen, "isDeleted",                             rs, "isDeleted");
        wjson(gen, "additionalFields",                   rs, "additionalDetails");
        wAudit(gen, rs);
        wClientAudit(gen, rs);
    }

    private void writeReferral(JsonGenerator gen, ResultSet rs) throws IOException, SQLException {
        ws(gen, "id",                                  rs, "id");
        ws(gen, "clientReferenceId",                   rs, "clientReferenceId");
        ws(gen, "tenantId",                            rs, "tenantId");
        ws(gen, "projectId",                           rs, "projectId");
        ws(gen, "projectBeneficiaryId",                rs, "projectBeneficiaryId");
        ws(gen, "projectBeneficiaryClientReferenceId", rs, "projectBeneficiaryClientReferenceId");
        ws(gen, "referrerId",                          rs, "referrerId");
        ws(gen, "recipientType",                       rs, "recipientType");
        ws(gen, "recipientId",                         rs, "recipientId");
        wjson(gen, "reasons",                          rs, "reasons");
        ws(gen, "referralCode",                        rs, "referralCode");
        wi(gen, "rowVersion",                          rs, "rowVersion");
        wb(gen, "isDeleted",                           rs, "isDeleted");
        wjson(gen, "additionalFields",                 rs, "additionalDetails");

        String sideEffectId = rs.getString("sideEffectId");
        if (sideEffectId != null) {
            gen.writeFieldName("sideEffect");
            gen.writeStartObject();
            ws(gen, "id",               rs, "sideEffectId");
            ws(gen, "clientReferenceId", rs, "sideEffectClientReferenceId");
            gen.writeEndObject();
        } else {
            gen.writeNullField("sideEffect");
        }

        wAudit(gen, rs);
        wClientAudit(gen, rs);
    }

    private void writeHfReferral(JsonGenerator gen, ResultSet rs) throws IOException, SQLException {
        ws(gen, "id",                rs, "id");
        ws(gen, "clientReferenceId", rs, "clientReferenceId");
        ws(gen, "tenantId",          rs, "tenantId");
        ws(gen, "projectId",         rs, "projectId");
        ws(gen, "projectFacilityId", rs, "projectFacilityId");
        ws(gen, "symptom",           rs, "symptom");
        ws(gen, "symptomSurveyId",   rs, "symptomSurveyId");
        ws(gen, "beneficiaryId",     rs, "beneficiaryId");
        ws(gen, "referralCode",      rs, "referralCode");
        ws(gen, "nationalLevelId",   rs, "nationalLevelId");
        wi(gen, "rowVersion",        rs, "rowVersion");
        wb(gen, "isDeleted",         rs, "isDeleted");
        wjson(gen, "additionalFields", rs, "additionalDetails");
        wAudit(gen, rs);
        wClientAudit(gen, rs);
    }

    private void writeProjectTask(JsonGenerator gen, ResultSet rs) throws IOException, SQLException {
        ws(gen, "id",                                  rs, "id");
        ws(gen, "clientReferenceId",                   rs, "clientReferenceId");
        ws(gen, "tenantId",                            rs, "tenantId");
        ws(gen, "projectId",                           rs, "projectId");
        ws(gen, "projectBeneficiaryId",                rs, "projectBeneficiaryId");
        ws(gen, "projectBeneficiaryClientReferenceId", rs, "projectBeneficiaryClientReferenceId");
        wl(gen, "plannedStartDate",                    rs, "plannedStartDate");
        wl(gen, "plannedEndDate",                      rs, "plannedEndDate");
        wl(gen, "actualStartDate",                     rs, "actualStartDate");
        wl(gen, "actualEndDate",                       rs, "actualEndDate");
        ws(gen, "status",                              rs, "status");
        wi(gen, "rowVersion",                          rs, "rowVersion");
        wb(gen, "isDeleted",                           rs, "isDeleted");
        wjson(gen, "additionalFields",                 rs, "additionalDetails");
        wjson(gen, "address",                          rs, "address_json");
        wjson(gen, "resources",                        rs, "resources_json");
        wAudit(gen, rs);
        wClientAudit(gen, rs);
    }

    // ── Individual decrypt-and-stream (buffer per cursor page) ───────────────

    private long streamIndividualQuery(GZIPOutputStream gzip, String namedSql, Map<String, Object> params) {
        ParsedSql parsed = NamedParameterUtils.parseSqlStatement(namedSql);
        SqlParameterSource paramSource = new MapSqlParameterSource(params);
        String positionalSql = NamedParameterUtils.substituteNamedParameters(parsed, paramSource);
        Object[] positionalParams = NamedParameterUtils.buildValueArray(parsed, paramSource, null);

        long[] count = {0};
        readOnlyTx.execute(status -> {
            namedJdbcTemplate.getJdbcTemplate().query(
                con -> {
                    PreparedStatement ps = con.prepareStatement(positionalSql,
                            ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                    ps.setFetchSize(CURSOR_FETCH_SIZE);
                    for (int i = 0; i < positionalParams.length; i++)
                        ps.setObject(i + 1, positionalParams[i]);
                    return ps;
                },
                (ResultSet rs) -> {
                    try {
                        JsonGenerator gen = objectMapper.getFactory().createGenerator(gzip);
                        gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
                        try {
                            List<Map<String, Object>> buffer = new ArrayList<>(CURSOR_FETCH_SIZE);
                            while (rs.next()) {
                                buffer.add(readIndividualRow(rs));
                                if (buffer.size() == CURSOR_FETCH_SIZE) {
                                    decryptIndividualBatch(buffer);
                                    writeIndividualBuffer(gen, buffer);
                                    count[0] += buffer.size();
                                    buffer.clear();
                                }
                            }
                            if (!buffer.isEmpty()) {
                                decryptIndividualBatch(buffer);
                                writeIndividualBuffer(gen, buffer);
                                count[0] += buffer.size();
                            }
                        } finally {
                            gen.flush();
                            gen.close();
                        }
                    } catch (IOException | SQLException e) {
                        throw new CustomException("INDIVIDUAL_STREAM_ERROR",
                                "Failed to buffer or write INDIVIDUAL rows during NDJSON generation. Cause: " + e.getMessage());
                    }
                    return null;
                }
            );
            return null;
        });
        return count[0];
    }

    private Map<String, Object> readIndividualRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>(IND_COLS.size() * 2);
        for (String col : IND_COLS)
            row.put(col, rs.getObject(col));
        return row;
    }

    private void decryptIndividualBatch(List<Map<String, Object>> batch) {
        // Build a per-row plan capturing which fields are encrypted and which identifier
        // positions need decryption. The payload sent to enc-service contains ONLY
        // ciphertext-bearing fields (no identifierType or other plaintext alongside) —
        // enc-service walks the JSON tree decrypting every string value, so any plaintext
        // value sent in the payload would cause an "Invalid Ciphertext" 500.
        List<DecryptPlan> plans = new ArrayList<>();
        ArrayNode payload = objectMapper.createArrayNode();

        for (int i = 0; i < batch.size(); i++) {
            Map<String, Object> row = batch.get(i);
            String mobile = (String) row.get("mobileNumber");
            JsonNode identifiers = parseIdentifiersJson(row.get("identifiers_json"));
            boolean mobileEnc = isCipherText(mobile);

            List<Integer> encryptedIdIdx = new ArrayList<>();
            if (identifiers != null && identifiers.isArray()) {
                for (int k = 0; k < identifiers.size(); k++) {
                    JsonNode iid = identifiers.get(k).get("identifierId");
                    if (iid != null && isCipherText(iid.asText(null))) {
                        encryptedIdIdx.add(k);
                    }
                }
            }
            if (!mobileEnc && encryptedIdIdx.isEmpty()) continue;

            ObjectNode node = objectMapper.createObjectNode();
            if (mobileEnc) node.put("mobileNumber", mobile);
            if (!encryptedIdIdx.isEmpty()) {
                ArrayNode filteredIds = objectMapper.createArrayNode();
                for (int k : encryptedIdIdx) {
                    ObjectNode flat = objectMapper.createObjectNode();
                    flat.put("identifierId", identifiers.get(k).get("identifierId").asText());
                    filteredIds.add(flat);
                }
                node.set("identifiers", filteredIds);
            }
            payload.add(node);
            plans.add(new DecryptPlan(i, mobileEnc, encryptedIdIdx, identifiers));
        }

        if (plans.isEmpty()) return;

        String decryptUrl = config.getEncHost() + config.getEncDecryptEndpoint();
        JsonNode decrypted;
        try {
            decrypted = restTemplate.postForObject(decryptUrl, payload, JsonNode.class);
        } catch (RestClientException e) {
            throw new CustomException("ENC_SERVICE_DECRYPT_FAILED",
                    "HTTP call to enc-service decrypt endpoint '" + decryptUrl + "' failed for a batch of "
                    + plans.size() + " individual(s). Verify egov.enc.host and egov.enc.decrypt.endpoint config. Cause: " + e.getMessage());
        }

        if (decrypted == null || !decrypted.isArray()) {
            throw new CustomException("ENC_SERVICE_INVALID_RESPONSE",
                    "Enc-service decrypt endpoint '" + decryptUrl + "' returned a null or non-array response "
                    + "for a batch of " + plans.size() + " individual(s). Expected a JSON array of the same size.");
        }

        if (decrypted.size() != plans.size()) {
            throw new CustomException("ENC_SERVICE_RESPONSE_SIZE_MISMATCH",
                    "Enc-service decrypt endpoint returned " + decrypted.size() + " element(s) but "
                    + plans.size() + " were sent. Cannot safely map decrypted values back to their rows.");
        }

        for (int j = 0; j < plans.size(); j++) {
            DecryptPlan plan = plans.get(j);
            Map<String, Object> row = batch.get(plan.rowIndex);
            JsonNode dec = decrypted.get(j);

            if (plan.mobileEnc && dec.has("mobileNumber")) {
                row.put("mobileNumber", dec.get("mobileNumber").asText(null));
            }

            if (!plan.encryptedIdIdx.isEmpty() && dec.has("identifiers") && dec.get("identifiers").isArray()) {
                JsonNode decIds = dec.get("identifiers");
                // Merge decrypted identifierId values back into the ORIGINAL identifiers array,
                // preserving identifierType and any other sibling fields.
                ArrayNode merged = (ArrayNode) plan.originalIdentifiers;
                for (int k = 0; k < plan.encryptedIdIdx.size(); k++) {
                    int targetIdx = plan.encryptedIdIdx.get(k);
                    JsonNode decId = decIds.get(k);
                    if (decId != null && decId.has("identifierId")
                            && merged.get(targetIdx) instanceof ObjectNode targetObj) {
                        targetObj.put("identifierId", decId.get("identifierId").asText(null));
                    }
                }
                row.put("identifiers_json", merged.toString());
            }
        }
    }

    private static final class DecryptPlan {
        final int rowIndex;
        final boolean mobileEnc;
        final List<Integer> encryptedIdIdx;
        final JsonNode originalIdentifiers;
        DecryptPlan(int rowIndex, boolean mobileEnc, List<Integer> encryptedIdIdx, JsonNode originalIdentifiers) {
            this.rowIndex = rowIndex;
            this.mobileEnc = mobileEnc;
            this.encryptedIdIdx = encryptedIdIdx;
            this.originalIdentifiers = originalIdentifiers;
        }
    }

    private static boolean isCipherText(String text) {
        // EGOV cipher format: <keyId>|<base64>. keyId is either a short numeric key-version
        // identifier (e.g. "104227") or a 36-char UUID — match both by only requiring a
        // non-empty prefix, a '|' separator, and a valid base64 suffix.
        if (text == null) return false;
        int pipe = text.indexOf('|');
        if (pipe <= 0 || pipe == text.length() - 1) return false;
        String base64 = text.substring(pipe + 1);
        if (base64.length() % 4 != 0) return false;
        for (int i = 0; i < base64.length(); i++) {
            char c = base64.charAt(i);
            boolean isB64 = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/' || c == '=';
            if (!isB64) return false;
        }
        return true;
    }

    private JsonNode parseIdentifiersJson(Object pgObj) {
        if (pgObj == null) return null;
        String json = pgObj instanceof PGobject pg ? pg.getValue() : pgObj.toString();
        if (json == null) return null;
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new CustomException("IDENTIFIERS_JSON_PARSE_ERROR",
                    "Failed to parse identifiers_json column value as JSON array. " +
                    "Raw value starts with: '" + json.substring(0, Math.min(json.length(), 100)) + "'. Cause: " + e.getMessage());
        }
    }


    private void writeIndividualBuffer(JsonGenerator gen, List<Map<String, Object>> buffer)
            throws IOException {
        for (Map<String, Object> row : buffer) {
            gen.writeStartObject();
            gen.writeStringField("_t", "INDIVIDUAL");
            writeIndividualFromMap(gen, row);
            gen.writeEndObject();
            gen.writeRaw('\n');
        }
    }

    private void writeIndividualFromMap(JsonGenerator gen, Map<String, Object> row) throws IOException {
        wsm(gen, "id",                row, "id");
        wsm(gen, "clientReferenceId", row, "clientReferenceId");
        wsm(gen, "tenantId",          row, "tenantId");
        wsm(gen, "individualId",      row, "individualId");
        wsm(gen, "userId",            row, "userId");
        wsm(gen, "userUuid",          row, "userUuid");

        gen.writeFieldName("name");
        gen.writeStartObject();
        wsm(gen, "givenName",  row, "givenName");
        wsm(gen, "familyName", row, "familyName");
        wsm(gen, "otherNames", row, "otherNames");
        gen.writeEndObject();

        Object dob = row.get("dateOfBirth");
        if (dob == null) gen.writeNullField("dateOfBirth");
        else gen.writeStringField("dateOfBirth", ((java.sql.Date) dob).toLocalDate().format(DOB_FMT));

        wsm(gen, "gender",             row, "gender");
        wsm(gen, "bloodGroup",         row, "bloodGroup");
        wsm(gen, "mobileNumber",       row, "mobileNumber");
        wsm(gen, "altContactNumber",   row, "altContactNumber");
        wsm(gen, "email",              row, "email");
        wsm(gen, "fatherName",         row, "fatherName");
        wsm(gen, "husbandName",        row, "husbandName");
        wsm(gen, "relationship",       row, "relationship");
        wsm(gen, "photo",              row, "photo");
        wbm(gen, "isSystemUser",       row, "isSystemUser");
        wbm(gen, "isSystemUserActive", row, "isSystemUserActive");
        wim(gen, "rowVersion",         row, "rowVersion");
        wbm(gen, "isDeleted",          row, "isDeleted");
        wjsonm(gen, "additionalFields", row, "additionalDetails");
        wjsonm(gen, "address",         row, "addresses_json");
        wjsonm(gen, "identifiers",     row, "identifiers_json");
        wjsonm(gen, "skills",          row, "skills_json");

        gen.writeFieldName("auditDetails");
        gen.writeStartObject();
        wsm(gen, "createdBy",        row, "createdBy");
        wlm(gen, "createdTime",      row, "createdTime");
        wsm(gen, "lastModifiedBy",   row, "lastModifiedBy");
        wlm(gen, "lastModifiedTime", row, "lastModifiedTime");
        gen.writeEndObject();

        gen.writeFieldName("clientAuditDetails");
        gen.writeStartObject();
        wsm(gen, "createdBy",        row, "clientCreatedBy");
        wlm(gen, "createdTime",      row, "clientCreatedTime");
        wsm(gen, "lastModifiedBy",   row, "clientLastModifiedBy");
        wlm(gen, "lastModifiedTime", row, "clientLastModifiedTime");
        gen.writeEndObject();
    }

    // ── Map-based write helpers (mirror RS-based ones for buffered individual rows) ──

    private void wsm(JsonGenerator gen, String field, Map<String, Object> row, String col)
            throws IOException {
        Object v = row.get(col);
        if (v == null) gen.writeNullField(field);
        else gen.writeStringField(field, (String) v);
    }

    private void wlm(JsonGenerator gen, String field, Map<String, Object> row, String col)
            throws IOException {
        Object v = row.get(col);
        if (v == null) gen.writeNullField(field);
        else gen.writeNumberField(field, ((Number) v).longValue());
    }

    private void wim(JsonGenerator gen, String field, Map<String, Object> row, String col)
            throws IOException {
        Object v = row.get(col);
        if (v == null) gen.writeNullField(field);
        else gen.writeNumberField(field, ((Number) v).intValue());
    }

    private void wbm(JsonGenerator gen, String field, Map<String, Object> row, String col)
            throws IOException {
        Object v = row.get(col);
        if (v == null) gen.writeNullField(field);
        else gen.writeBooleanField(field, (Boolean) v);
    }

    private void wjsonm(JsonGenerator gen, String field, Map<String, Object> row, String col)
            throws IOException {
        Object val = row.get(col);
        if (val == null) {
            gen.writeNullField(field);
        } else if (val instanceof PGobject pg) {
            gen.writeFieldName(field);
            String v = pg.getValue();
            if (v != null) gen.writeRawValue(v);
            else gen.writeNull();
        } else if (val instanceof String s) {
            // identifiers_json patched in-place as String after decrypt
            gen.writeFieldName(field);
            gen.writeRawValue(s);
        } else {
            gen.writeNullField(field);
        }
    }

    // ── JSON write helpers ────────────────────────────────────────────────────

    private void ws(JsonGenerator gen, String field, ResultSet rs, String col)
            throws IOException, SQLException {
        String v = rs.getString(col);
        if (v == null) gen.writeNullField(field);
        else gen.writeStringField(field, v);
    }

    private void wl(JsonGenerator gen, String field, ResultSet rs, String col)
            throws IOException, SQLException {
        long v = rs.getLong(col);
        if (rs.wasNull()) gen.writeNullField(field);
        else gen.writeNumberField(field, v);
    }

    private void wi(JsonGenerator gen, String field, ResultSet rs, String col)
            throws IOException, SQLException {
        int v = rs.getInt(col);
        if (rs.wasNull()) gen.writeNullField(field);
        else gen.writeNumberField(field, v);
    }

    private void wd(JsonGenerator gen, String field, ResultSet rs, String col)
            throws IOException, SQLException {
        double v = rs.getDouble(col);
        if (rs.wasNull()) gen.writeNullField(field);
        else gen.writeNumberField(field, v);
    }

    private void wb(JsonGenerator gen, String field, ResultSet rs, String col)
            throws IOException, SQLException {
        boolean v = rs.getBoolean(col);
        if (rs.wasNull()) gen.writeNullField(field);
        else gen.writeBooleanField(field, v);
    }

    private void wjson(JsonGenerator gen, String field, ResultSet rs, String col)
            throws IOException, SQLException {
        Object val = rs.getObject(col);
        if (val == null) {
            gen.writeNullField(field);
        } else if (val instanceof PGobject pg) {
            gen.writeFieldName(field);
            String v = pg.getValue();
            if (v != null) gen.writeRawValue(v);
            else gen.writeNull();
        } else {
            gen.writeNullField(field);
        }
    }

    private void wAudit(JsonGenerator gen, ResultSet rs) throws IOException, SQLException {
        gen.writeFieldName("auditDetails");
        gen.writeStartObject();
        ws(gen, "createdBy",       rs, "createdBy");
        wl(gen, "createdTime",     rs, "createdTime");
        ws(gen, "lastModifiedBy",  rs, "lastModifiedBy");
        wl(gen, "lastModifiedTime", rs, "lastModifiedTime");
        gen.writeEndObject();
    }

    private void wClientAudit(JsonGenerator gen, ResultSet rs) throws IOException, SQLException {
        gen.writeFieldName("clientAuditDetails");
        gen.writeStartObject();
        ws(gen, "createdBy",       rs, "clientCreatedBy");
        wl(gen, "createdTime",     rs, "clientCreatedTime");
        ws(gen, "lastModifiedBy",  rs, "clientLastModifiedBy");
        wl(gen, "lastModifiedTime", rs, "clientLastModifiedTime");
        gen.writeEndObject();
    }

    private void writeLocality(JsonGenerator gen, ResultSet rs, String col)
            throws IOException, SQLException {
        String code = rs.getString(col);
        if (code == null) {
            gen.writeNullField("locality");
        } else {
            gen.writeFieldName("locality");
            gen.writeStartObject();
            gen.writeStringField("code", code);
            gen.writeEndObject();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> localityParams(LocalityDownsyncCriteria c) {
        return Map.of("locality", c.getLocality());
    }

    private Map<String, Object> projectLocalityParams(LocalityDownsyncCriteria c) {
        return Map.of("projectId", c.getProjectId(), "locality", c.getLocality());
    }

    private String resolveSql(String template, String tenantId) {
        try {
            return multiStateInstanceUtil.replaceSchemaPlaceholder(template, tenantId);
        } catch (InvalidTenantIdException e) {
            throw new CustomException("INVALID_TENANT_ID",
                    "Cannot resolve DB schema for tenantId '" + tenantId + "'. " +
                    "Verify 'is.environment.central.instance' and 'state.schema.index.position.tenantid' config. Cause: " + e.getMessage());
        }
    }

    private String s3RegistryKey(LocalityDownsyncCriteria c, String fileType) {
        return c.getTenantId() + "/" + c.getLocality() + "/" + fileType + ".ndjson.gz";
    }

    private String s3ProjectKey(LocalityDownsyncCriteria c, String fileType) {
        String rootProjectId = c.getRootProjectId() != null ? c.getRootProjectId() : c.getProjectId();
        return c.getTenantId() + "/" + rootProjectId + "/" + c.getLocality() + "/" + fileType + ".ndjson.gz";
    }

    private String truncate(String msg) {
        if (msg == null) return "unknown";
        return msg.length() > 2000 ? msg.substring(0, 2000) : msg;
    }

    private record FileResult(String fileType, boolean success, String s3Key,
                               long recordCount, String failureReason) {}
}
