package org.egov.referralmanagement.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.household.Address;
import org.egov.common.models.household.Household;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.DownsyncGenerationJobRepository;
import org.egov.referralmanagement.service.DownsyncS3Service.S3Result;
import org.egov.referralmanagement.web.models.LocalityDownsyncCriteria;
import org.egov.tracer.config.TracerProperties;
import org.egov.tracer.http.RestTemplateLoggingInterceptor;
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
    @Autowired private TracerProperties tracerProperties;

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
        wardPool   = Executors.newFixedThreadPool(config.getWardPoolSize());
        readOnlyTx = new TransactionTemplate(txManager);
        readOnlyTx.setReadOnly(true);
        SimpleClientHttpRequestFactory httpFactory = new SimpleClientHttpRequestFactory();
        httpFactory.setConnectTimeout(5_000);
        httpFactory.setReadTimeout(30_000);
        restTemplate = new RestTemplate(httpFactory);
        restTemplate.getInterceptors().add(new RestTemplateLoggingInterceptor(tracerProperties));
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
            List<String> fileTypesToRun = jobRepository.findResumableFileTypes(tid, localityRowId);
            if (fileTypesToRun.isEmpty()) {
                jobRepository.updateLocalityCompleted(tid, localityRowId, "SUCCESS", null, System.currentTimeMillis());
                return;
            }

            int skipped = 0, failed = 0;
            for (String ft : fileTypesToRun) {
                if (!criteria.isForceRefresh()) {
                    String skipReason = getFileSkipReason(criteria, ft);
                    if (skipReason != null) {
                        log.info("File SKIPPED — type={} locality={} reason={}", ft, criteria.getLocality(), skipReason);
                        jobRepository.updateFileCompleted(tid, localityRowId, ft, "SKIPPED",
                                null, null, null, skipReason, System.currentTimeMillis());
                        skipped++;
                        continue;
                    }
                }
                if (!runRegistryFile(ft, criteria, localityRowId).success()) failed++;
            }

            String localityStatus;
            if (skipped == fileTypesToRun.size())   localityStatus = "SKIPPED";
            else if (failed == 0)                   localityStatus = "SUCCESS";
            else                                    localityStatus = "PARTIAL_SUCCESS";

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
            List<String> fileTypesToRun = jobRepository.findResumableFileTypes(tid, localityRowId);
            if (fileTypesToRun.isEmpty()) {
                jobRepository.updateLocalityCompleted(tid, localityRowId, "SUCCESS", null, System.currentTimeMillis());
                return;
            }

            int skipped = 0, failed = 0;
            for (String ft : fileTypesToRun) {
                if (!criteria.isForceRefresh()) {
                    String skipReason = getFileSkipReason(criteria, ft);
                    if (skipReason != null) {
                        log.info("File SKIPPED — type={} locality={} reason={}", ft, criteria.getLocality(), skipReason);
                        jobRepository.updateFileCompleted(tid, localityRowId, ft, "SKIPPED",
                                null, null, null, skipReason, System.currentTimeMillis());
                        skipped++;
                        continue;
                    }
                }
                if (!runProjectFile(ft, criteria, localityRowId).success()) failed++;
            }

            String localityStatus;
            if (skipped == fileTypesToRun.size())   localityStatus = "SKIPPED";
            else if (failed == 0)                   localityStatus = "SUCCESS";
            else                                    localityStatus = "PARTIAL_SUCCESS";

            jobRepository.updateLocalityCompleted(tid, localityRowId, localityStatus, null, System.currentTimeMillis());
            log.info("Project locality {} → {}", criteria.getLocality(), localityStatus);
        } catch (Exception e) {
            log.error("Project locality {} FAILED: {}", criteria.getLocality(), e.getMessage());
            jobRepository.updateLocalityCompleted(tid, localityRowId, "FAILED",
                    truncate(e.getMessage()), System.currentTimeMillis());
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

    private FileResult runRegistryFile(String fileType, LocalityDownsyncCriteria c, String rowId) {
        return switch (fileType) {
            case "HH_MEMBERS"  -> streamHhMembersFile(c, rowId);
            case "INDIVIDUALS" -> streamIndividualsFile(c, rowId);
            default -> throw new CustomException("UNKNOWN_REGISTRY_FILE_TYPE",
                    "Unrecognised registry file type '" + fileType + "'. Expected one of: HH_MEMBERS, INDIVIDUALS.");
        };
    }

    private FileResult runProjectFile(String fileType, LocalityDownsyncCriteria c, String rowId) {
        return switch (fileType) {
            case "BENE_AE_REF" -> streamBeneAeRefFile(c, rowId);
            case "TASKS"       -> streamTasksFile(c, rowId);
            default -> throw new CustomException("UNKNOWN_PROJECT_FILE_TYPE",
                    "Unrecognised project file type '" + fileType + "'. Expected one of: BENE_AE_REF, TASKS.");
        };
    }

    // ── File streaming methods ────────────────────────────────────────────────

    private FileResult streamHhMembersFile(LocalityDownsyncCriteria c, String rowId) {
        String tid = c.getTenantId();
        String key = s3RegistryKey(c, "hh_members");
        jobRepository.updateFileStarted(tid, rowId, "HH_MEMBERS", System.currentTimeMillis());
        try {
            S3Result s3 = s3Service.streamToS3(key, gzip -> {
                long n = streamHouseholdQuery(gzip, resolveSql(HH_QUERY, tid), localityParams(c));
                return n + streamQuery(gzip, resolveSql(HH_MEMBER_QUERY, tid), localityParams(c), "HOUSEHOLD_MEMBER");
            });
            jobRepository.updateFileCompleted(tid, rowId, "HH_MEMBERS", "SUCCESS",
                    s3.rowCount() > 0 ? key : null, s3.rowCount(), s3.fileSize(), null, System.currentTimeMillis());
            return new FileResult("HH_MEMBERS", true, s3.rowCount() > 0 ? key : null, s3.rowCount(), null);
        } catch (Exception e) {
            jobRepository.updateFileCompleted(tid, rowId, "HH_MEMBERS", "FAILED",
                    null, null, null, truncate(e.getMessage()), System.currentTimeMillis());
            return new FileResult("HH_MEMBERS", false, null, 0, e.getMessage());
        }
    }

    private FileResult streamIndividualsFile(LocalityDownsyncCriteria c, String rowId) {
        String tid = c.getTenantId();
        String key = s3RegistryKey(c, "individuals");
        jobRepository.updateFileStarted(tid, rowId, "INDIVIDUALS", System.currentTimeMillis());
        try {
            S3Result s3 = s3Service.streamToS3(key, gzip ->
                    streamIndividualQuery(gzip, resolveSql(INDIVIDUAL_QUERY, tid), localityParams(c)));
            jobRepository.updateFileCompleted(tid, rowId, "INDIVIDUALS", "SUCCESS",
                    s3.rowCount() > 0 ? key : null, s3.rowCount(), s3.fileSize(), null, System.currentTimeMillis());
            return new FileResult("INDIVIDUALS", true, s3.rowCount() > 0 ? key : null, s3.rowCount(), null);
        } catch (Exception e) {
            jobRepository.updateFileCompleted(tid, rowId, "INDIVIDUALS", "FAILED",
                    null, null, null, truncate(e.getMessage()), System.currentTimeMillis());
            return new FileResult("INDIVIDUALS", false, null, 0, e.getMessage());
        }
    }

    private FileResult streamBeneAeRefFile(LocalityDownsyncCriteria c, String rowId) {
        String tid = c.getTenantId();
        String key = s3ProjectKey(c, "bene_ae_ref");
        jobRepository.updateFileStarted(tid, rowId, "BENE_AE_REF", System.currentTimeMillis());
        try {
            S3Result s3 = s3Service.streamToS3(key, gzip -> {
                long n = streamQuery(gzip, resolveSql(BENEFICIARY_QUERY, tid), projectLocalityParams(c), "PROJECT_BENEFICIARY");
                n += streamQuery(gzip, resolveSql(SIDE_EFFECT_QUERY, tid), projectLocalityParams(c), "SIDE_EFFECT");
                n += streamQuery(gzip, resolveSql(REFERRAL_QUERY, tid), projectLocalityParams(c), "REFERRAL");
                return n + streamQuery(gzip, resolveSql(HF_REFERRAL_QUERY, tid), projectLocalityParams(c), "HF_REFERRAL");
            });
            jobRepository.updateFileCompleted(tid, rowId, "BENE_AE_REF", "SUCCESS",
                    s3.rowCount() > 0 ? key : null, s3.rowCount(), s3.fileSize(), null, System.currentTimeMillis());
            return new FileResult("BENE_AE_REF", true, s3.rowCount() > 0 ? key : null, s3.rowCount(), null);
        } catch (Exception e) {
            jobRepository.updateFileCompleted(tid, rowId, "BENE_AE_REF", "FAILED",
                    null, null, null, truncate(e.getMessage()), System.currentTimeMillis());
            return new FileResult("BENE_AE_REF", false, null, 0, e.getMessage());
        }
    }

    private FileResult streamTasksFile(LocalityDownsyncCriteria c, String rowId) {
        String tid = c.getTenantId();
        String key = s3ProjectKey(c, "tasks");
        jobRepository.updateFileStarted(tid, rowId, "TASKS", System.currentTimeMillis());
        try {
            S3Result s3 = s3Service.streamToS3(key, gzip ->
                    streamQuery(gzip, resolveSql(TASK_QUERY, tid), projectLocalityParams(c), "PROJECT_TASK"));
            jobRepository.updateFileCompleted(tid, rowId, "TASKS", "SUCCESS",
                    s3.rowCount() > 0 ? key : null, s3.rowCount(), s3.fileSize(), null, System.currentTimeMillis());
            return new FileResult("TASKS", true, s3.rowCount() > 0 ? key : null, s3.rowCount(), null);
        } catch (Exception e) {
            jobRepository.updateFileCompleted(tid, rowId, "TASKS", "FAILED",
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

    // ── Household decrypt-and-stream (buffer per cursor page) ────────────────

    /** Columns of household_address_mv that the HOUSEHOLD rows of the file are written from. */
    private static final List<String> HH_COLS = List.of(
            "id", "clientReferenceId", "tenantId", "numberOfMembers", "householdType", "rowVersion", "isDeleted",
            "additionalDetails",
            "createdBy", "createdTime", "lastModifiedBy", "lastModifiedTime",
            "clientCreatedBy", "clientCreatedTime", "clientLastModifiedBy", "clientLastModifiedTime",
            "aid", "aclientreferenceid", "atenantid", "doorNo", "latitude", "longitude", "locationAccuracy", "type",
            "addressLine1", "addressLine2", "landmark", "city", "pinCode", "buildingName", "street", "localityCode"
    );
    /** Address text columns that the household service stores as cipher text (latitude/longitude stay plain). */
    private static final List<String> HH_ADDRESS_CIPHER_COLS = List.of(
            "doorNo", "addressLine1", "addressLine2", "landmark", "city", "pinCode", "buildingName", "street");

    private long streamHouseholdQuery(GZIPOutputStream gzip, String namedSql, Map<String, Object> params) {
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
                                buffer.add(readHouseholdRow(rs));
                                if (buffer.size() == CURSOR_FETCH_SIZE) {
                                    decryptHouseholdBatch(buffer);
                                    writeHouseholdBuffer(gen, buffer);
                                    count[0] += buffer.size();
                                    buffer.clear();
                                }
                            }
                            if (!buffer.isEmpty()) {
                                decryptHouseholdBatch(buffer);
                                writeHouseholdBuffer(gen, buffer);
                                count[0] += buffer.size();
                            }
                        } finally {
                            gen.flush();
                            gen.close();
                        }
                    } catch (IOException | SQLException e) {
                        throw new CustomException("HOUSEHOLD_STREAM_ERROR",
                                "Failed to buffer or write HOUSEHOLD rows during NDJSON generation. Cause: " + e.getMessage());
                    }
                    return null;
                }
            );
            return null;
        });
        return count[0];
    }

    private Map<String, Object> readHouseholdRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>(HH_COLS.size() * 2);
        for (String col : HH_COLS)
            row.put(col, rs.getObject(col));
        return row;
    }

    /**
     * Decrypts the address text values of one page of household rows with ONE enc-service call, so the downsync
     * file carries the same plain values as before encryption at rest. Only real cipher text is sent; rows not
     * yet backfilled (plain values) pass through unchanged.
     */
    private void decryptHouseholdBatch(List<Map<String, Object>> batch) {
        ArrayNode payload = objectMapper.createArrayNode();
        List<java.util.function.Consumer<String>> setters = new ArrayList<>();
        for (Map<String, Object> row : batch) {
            for (String col : HH_ADDRESS_CIPHER_COLS) {
                collectCipher(row.get(col) instanceof String text ? text : null, plain -> row.put(col, plain),
                        payload, setters);
            }
        }
        if (payload.isEmpty()) return;
        decryptCiphers(payload, setters, batch.size(), "household");
    }

    /**
     * Same decrypt for the households of the matview search (DownsyncService), which the row mapper builds from
     * the same household_address_mv rows. One enc-service call for the whole page; plain values pass through.
     */
    public void decryptHouseholdAddresses(List<Household> households) {
        if (households == null || households.isEmpty()) return;
        ArrayNode payload = objectMapper.createArrayNode();
        List<java.util.function.Consumer<String>> setters = new ArrayList<>();
        for (Household household : households) {
            Address a = household.getAddress();
            if (a == null) continue;
            collectCipher(a.getDoorNo(), a::setDoorNo, payload, setters);
            collectCipher(a.getAddressLine1(), a::setAddressLine1, payload, setters);
            collectCipher(a.getAddressLine2(), a::setAddressLine2, payload, setters);
            collectCipher(a.getLandmark(), a::setLandmark, payload, setters);
            collectCipher(a.getCity(), a::setCity, payload, setters);
            collectCipher(a.getPincode(), a::setPincode, payload, setters);
            collectCipher(a.getBuildingName(), a::setBuildingName, payload, setters);
            collectCipher(a.getStreet(), a::setStreet, payload, setters);
        }
        if (payload.isEmpty()) return;
        decryptCiphers(payload, setters, households.size(), "household");
    }

    private void collectCipher(String value, java.util.function.Consumer<String> setter, ArrayNode payload,
                               List<java.util.function.Consumer<String>> setters) {
        if (isCipherText(value)) {
            payload.add(value);
            setters.add(setter);
        }
    }

    private void writeHouseholdBuffer(JsonGenerator gen, List<Map<String, Object>> buffer) throws IOException {
        for (Map<String, Object> row : buffer) {
            gen.writeStartObject();
            gen.writeStringField("_t", "HOUSEHOLD");
            writeHouseholdFromMap(gen, row);
            gen.writeEndObject();
            gen.writeRaw('\n');
        }
    }

    /** Same output as {@link #writeHousehold} (which reads a ResultSet), for a buffered and decrypted row. */
    private void writeHouseholdFromMap(JsonGenerator gen, Map<String, Object> row) throws IOException {
        wsm(gen, "id",              row, "id");
        wsm(gen, "clientReferenceId", row, "clientReferenceId");
        wsm(gen, "tenantId",        row, "tenantId");
        wim(gen, "memberCount",     row, "numberOfMembers");
        wsm(gen, "householdType",   row, "householdType");
        wim(gen, "rowVersion",      row, "rowVersion");
        wbm(gen, "isDeleted",       row, "isDeleted");
        wjsonm(gen, "additionalFields", row, "additionalDetails");

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

        if (row.get("aid") == null) {
            gen.writeNullField("address");
        } else {
            gen.writeFieldName("address");
            gen.writeStartObject();
            wsm(gen, "id",               row, "aid");
            wsm(gen, "clientReferenceId", row, "aclientreferenceid");
            wsm(gen, "tenantId",         row, "atenantid");
            wsm(gen, "doorNo",           row, "doorNo");
            wdm(gen, "latitude",         row, "latitude");
            wdm(gen, "longitude",        row, "longitude");
            wdm(gen, "locationAccuracy", row, "locationAccuracy");
            wsm(gen, "type",             row, "type");
            wsm(gen, "addressLine1",     row, "addressLine1");
            wsm(gen, "addressLine2",     row, "addressLine2");
            wsm(gen, "landmark",         row, "landmark");
            wsm(gen, "city",             row, "city");
            wsm(gen, "pincode",          row, "pinCode");
            wsm(gen, "buildingName",     row, "buildingName");
            wsm(gen, "street",           row, "street");
            Object locality = row.get("localityCode");
            if (locality == null) {
                gen.writeNullField("locality");
            } else {
                gen.writeFieldName("locality");
                gen.writeStartObject();
                gen.writeStringField("code", (String) locality);
                gen.writeEndObject();
            }
            gen.writeEndObject();
        }
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

    /** Individual columns that the individual service stores as cipher text. */
    private static final List<String> IND_CIPHER_COLS = List.of(
            "givenName", "familyName", "otherNames", "dateOfBirth", "gender", "bloodGroup",
            "mobileNumber", "altContactNumber", "email", "fatherName", "husbandName");
    /** Text fields of an individual's address that are stored as cipher text. */
    private static final List<String> ADDRESS_CIPHER_FIELDS = List.of(
            "doorNo", "addressLine1", "addressLine2", "landmark", "city", "pincode", "buildingName", "street");

    /**
     * Decrypts every encrypted value of one page of individual rows with ONE enc-service call, so the
     * downsync file carries the same plain values as before encryption at rest.
     * Only real cipher text is sent: enc-service rejects any plain value it is given.
     * Rows not yet backfilled (plain values) pass through unchanged.
     */
    private void decryptIndividualBatch(List<Map<String, Object>> batch) {
        ArrayNode payload = objectMapper.createArrayNode();
        List<java.util.function.Consumer<String>> setters = new ArrayList<>();
        List<Runnable> afterDecrypt = new ArrayList<>();

        for (Map<String, Object> row : batch) {
            for (String col : IND_CIPHER_COLS) {
                Object v = row.get(col);
                if (v instanceof String text && isCipherText(text)) {
                    payload.add(text);
                    setters.add(plain -> row.put(col, plain));
                }
            }
            collectJsonCiphers(row, "additionalDetails", payload, setters, afterDecrypt,
                    node -> node.path("fields"), "value");
            collectJsonCiphers(row, "identifiers_json", payload, setters, afterDecrypt,
                    node -> node, "identifierId");
            collectJsonCiphers(row, "addresses_json", payload, setters, afterDecrypt,
                    node -> node, ADDRESS_CIPHER_FIELDS.toArray(new String[0]));
        }

        if (payload.isEmpty()) return;

        decryptCiphers(payload, setters, batch.size(), "individual");
        afterDecrypt.forEach(Runnable::run);
    }

    /**
     * Sends the collected cipher values to enc-service in ONE call and hands every plain value to its setter
     * (same order). Used for individuals and households.
     */
    private void decryptCiphers(ArrayNode payload, List<java.util.function.Consumer<String>> setters,
                                int entityCount, String entity) {
        String decryptUrl = config.getEncHost() + config.getEncDecryptEndpoint();
        JsonNode decrypted;
        try {
            decrypted = restTemplate.postForObject(decryptUrl, payload, JsonNode.class);
        } catch (RestClientException e) {
            throw new CustomException("ENC_SERVICE_DECRYPT_FAILED",
                    "HTTP call to enc-service decrypt endpoint '" + decryptUrl + "' failed for a batch of "
                    + payload.size() + " value(s) from " + entityCount + " " + entity + "(s). "
                    + "Verify egov.enc.host and egov.enc.decrypt.endpoint config. Cause: " + e.getClass().getSimpleName());
        }

        if (decrypted == null || !decrypted.isArray()) {
            throw new CustomException("ENC_SERVICE_INVALID_RESPONSE",
                    "Enc-service decrypt endpoint '" + decryptUrl + "' returned a null or non-array response "
                    + "for a batch of " + payload.size() + " value(s). Expected a JSON array of the same size.");
        }

        if (decrypted.size() != payload.size()) {
            throw new CustomException("ENC_SERVICE_RESPONSE_SIZE_MISMATCH",
                    "Enc-service decrypt endpoint returned " + decrypted.size() + " element(s) but "
                    + payload.size() + " were sent. Cannot safely map decrypted values back to their rows.");
        }

        for (int j = 0; j < setters.size(); j++) {
            JsonNode value = decrypted.get(j);
            setters.get(j).accept(value == null || value.isNull() ? null : value.asText());
        }
    }

    /**
     * Finds cipher values inside a JSON column (array of objects reached through {@code arrayOf}) and
     * queues them for decryption. After decryption the column is replaced by the patched JSON text.
     */
    private void collectJsonCiphers(Map<String, Object> row, String col, ArrayNode payload,
                                    List<java.util.function.Consumer<String>> setters, List<Runnable> afterDecrypt,
                                    java.util.function.Function<JsonNode, JsonNode> arrayOf, String... fields) {
        JsonNode root = parseIdentifiersJson(row.get(col));
        if (root == null) return;
        JsonNode array = arrayOf.apply(root);
        if (array == null || !array.isArray()) return;
        boolean found = false;
        for (JsonNode element : array) {
            if (!(element instanceof ObjectNode obj)) continue;
            for (String field : fields) {
                JsonNode v = obj.get(field);
                if (v != null && v.isTextual() && isCipherText(v.asText())) {
                    payload.add(v.asText());
                    setters.add(plain -> obj.put(field, plain));
                    found = true;
                }
            }
        }
        if (found) {
            afterDecrypt.add(() -> row.put(col, root.toString()));
        }
    }

    private static final java.util.regex.Pattern CIPHER_SHAPE =
            java.util.regex.Pattern.compile("^[A-Za-z0-9-]{1,64}\\|[A-Za-z0-9+/]+={0,2}$");

    /**
     * enc-service cipher text is {@code <keyId>|<base64>}; the key id is numeric on current enc-service
     * (it was a UUID on older versions). AES-GCM adds a 16-byte tag, so a real cipher decodes to at least
     * 16 bytes. Same rule as CipherText in health-services-common.
     */
    static boolean isCipherText(String text) {
        if (text == null || !CIPHER_SHAPE.matcher(text).matches()) return false;
        String base64 = text.substring(text.indexOf('|') + 1);
        if (base64.length() % 4 != 0) return false;
        try {
            return java.util.Base64.getDecoder().decode(base64).length >= 16;
        } catch (IllegalArgumentException e) {
            return false;
        }
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

    /** yyyy-MM-dd at the start of the text; the year may have more than 4 digits (the database writes 10000-01-01). */
    private static final java.util.regex.Pattern DOB_TEXT = java.util.regex.Pattern.compile("^(\\d{4,})-(\\d{2})-(\\d{2})");

    /**
     * dateOfBirth arrives as java.sql.Date (column still a date) or as text (TEXT column: plain from before the
     * backfill, or decrypted above): yyyy-MM-dd, or a timestamp text that starts with it (what an old persister
     * config writes into the text column), read the same way as the individual service. Output format is unchanged
     * (dd/MM/yyyy).
     */
    static java.time.LocalDate toLocalDate(Object dob) {
        if (dob instanceof java.sql.Date d) return d.toLocalDate();
        String text = dob.toString().trim();
        if (isCipherText(text)) {
            throw new CustomException("INDIVIDUAL_DOB_NOT_DECRYPTED", "dateOfBirth is still cipher text after decryption");
        }
        java.util.regex.Matcher m = DOB_TEXT.matcher(text);
        try {
            if (m.find()) {
                return java.time.LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)));
            }
        } catch (java.time.DateTimeException | NumberFormatException e) {
            // falls through to the error below
        }
        throw new CustomException("INDIVIDUAL_DOB_INVALID", "dateOfBirth is not a yyyy-MM-dd date");
    }

    /** A date of birth that cannot be read is written empty with a warning (never the value), not a failed job. */
    static java.time.LocalDate readDob(Object dob, Object individualId) {
        try {
            return toLocalDate(dob);
        } catch (CustomException e) {
            if (!"INDIVIDUAL_DOB_INVALID".equals(e.getCode())) throw e;
            log.warn("dateOfBirth of individual {} is not a yyyy-MM-dd date; written empty", individualId);
            return null;
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

        java.time.LocalDate dob = row.get("dateOfBirth") == null ? null : readDob(row.get("dateOfBirth"), row.get("id"));
        if (dob == null) gen.writeNullField("dateOfBirth");
        else gen.writeStringField("dateOfBirth", dob.format(DOB_FMT));

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

    private void wdm(JsonGenerator gen, String field, Map<String, Object> row, String col)
            throws IOException {
        Object v = row.get(col);
        if (v == null) gen.writeNullField(field);
        else gen.writeNumberField(field, ((Number) v).doubleValue());
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
