package org.egov.referralmanagement.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.DownsyncGenerationJobRepository;
import org.egov.referralmanagement.service.DownsyncS3Service.S3Result;
import org.egov.referralmanagement.service.DownsyncS3Service.StreamWriter;
import org.egov.referralmanagement.web.models.LocalityDownsyncCriteria;
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

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
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

    private static final String INDIVIDUAL_QUERY =
            "SELECT ind.* FROM {schema}.INDIVIDUAL ind " +
            "WHERE ind.clientReferenceId IN (" +
            "  SELECT hm.individualClientReferenceId " +
            "  FROM {schema}.HOUSEHOLD_MEMBER hm " +
            "  WHERE hm.isDeleted = false " +
            "  AND hm.householdClientReferenceId IN (" +
            "    SELECT clientReferenceId FROM {schema}.household_address_mv " +
            "    WHERE localitycode = :locality AND isdeleted = false" +
            "  )" +
            ") AND ind.isDeleted = false";

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
            "SELECT pt.* FROM {schema}.PROJECT_TASK pt " +
            "WHERE pt.projectId = :projectId AND pt.isDeleted = false " +
            "AND pt.projectBeneficiaryClientReferenceId IN (" + LOCALITY_BENE_SUBQUERY + ")";

    @Autowired private NamedParameterJdbcTemplate namedJdbcTemplate;
    @Autowired private MultiStateInstanceUtil multiStateInstanceUtil;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ReferralManagementConfiguration config;
    @Autowired @Qualifier("objectMapper") private ObjectMapper objectMapper;
    @Autowired private DownsyncGenerationJobRepository jobRepository;
    @Autowired private DownsyncS3Service s3Service;

    private ExecutorService wardPool;
    private TransactionTemplate readOnlyTx;

    @PostConstruct
    public void init() {
        wardPool   = Executors.newFixedThreadPool(config.getWardPoolSize());
        readOnlyTx = new TransactionTemplate(txManager);
        readOnlyTx.setReadOnly(true);
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
            if (!isRegistryStale(tid, criteria.getLocality())) {
                log.info("Registry SKIPPED (fresh) — locality={} tenant={}", criteria.getLocality(), tid);
                jobRepository.updateLocalityCompleted(tid, localityRowId, "SKIPPED", null, System.currentTimeMillis());
                markFilesSkipped(tid, localityRowId);
                return;
            }

            List<String> fileTypesToRun = jobRepository.findResumableFileTypes(tid, localityRowId);
            if (fileTypesToRun.isEmpty()) {
                jobRepository.updateLocalityCompleted(tid, localityRowId, "SUCCESS", null, System.currentTimeMillis());
                return;
            }

            boolean anyFailed = false;
            for (String ft : fileTypesToRun)
                if (!runRegistryFile(ft, criteria, localityRowId).success()) anyFailed = true;

            String localityStatus = anyFailed ? "PARTIAL_SUCCESS" : "SUCCESS";
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

            boolean anyFailed = false;
            for (String ft : fileTypesToRun)
                if (!runProjectFile(ft, criteria, localityRowId).success()) anyFailed = true;

            String localityStatus = anyFailed ? "PARTIAL_SUCCESS" : "SUCCESS";
            jobRepository.updateLocalityCompleted(tid, localityRowId, localityStatus, null, System.currentTimeMillis());
            log.info("Project locality {} → {}", criteria.getLocality(), localityStatus);
        } catch (Exception e) {
            log.error("Project locality {} FAILED: {}", criteria.getLocality(), e.getMessage());
            jobRepository.updateLocalityCompleted(tid, localityRowId, "FAILED",
                    truncate(e.getMessage()), System.currentTimeMillis());
        }
    }

    // ── Staleness check ───────────────────────────────────────────────────────

    public boolean isRegistryStale(String tenantId, String locality) {
        Long lastFileEndTime = jobRepository.findLatestRegistryFileEndTime(tenantId, locality);
        if (lastFileEndTime == null) return true;
        Long maxModified = jobRepository.findMaxHouseholdModifiedTime(tenantId, locality);
        if (maxModified == null) return false;
        return maxModified > lastFileEndTime;
    }

    // ── File dispatchers ──────────────────────────────────────────────────────

    private FileResult runRegistryFile(String fileType, LocalityDownsyncCriteria c, String rowId) {
        return switch (fileType) {
            case "HH_MEMBERS"  -> streamHhMembersFile(c, rowId);
            case "INDIVIDUALS" -> streamIndividualsFile(c, rowId);
            default -> throw new IllegalArgumentException("Unknown registry file type: " + fileType);
        };
    }

    private FileResult runProjectFile(String fileType, LocalityDownsyncCriteria c, String rowId) {
        return switch (fileType) {
            case "BENE_AE_REF" -> streamBeneAeRefFile(c, rowId);
            case "TASKS"       -> streamTasksFile(c, rowId);
            default -> throw new IllegalArgumentException("Unknown project file type: " + fileType);
        };
    }

    // ── File streaming methods ────────────────────────────────────────────────

    private FileResult streamHhMembersFile(LocalityDownsyncCriteria c, String rowId) {
        String tid = c.getTenantId();
        String key = s3RegistryKey(c, "hh_members");
        jobRepository.updateFileStarted(tid, rowId, "HH_MEMBERS", System.currentTimeMillis());
        try {
            S3Result s3 = s3Service.streamToS3(key, gzip -> {
                long n = streamQuery(gzip, resolveSql(HH_QUERY, tid), localityParams(c), "HOUSEHOLD");
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
                    streamQuery(gzip, resolveSql(INDIVIDUAL_QUERY, tid), localityParams(c), "INDIVIDUAL"));
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
                        ps.setFetchSize(1000);
                        for (int i = 0; i < positionalParams.length; i++)
                            ps.setObject(i + 1, positionalParams[i]);
                        return ps;
                    },
                    (ResultSet rs) -> {
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();
                        String[] colNames = new String[colCount];
                        for (int i = 0; i < colCount; i++)
                            colNames[i] = meta.getColumnLabel(i + 1);
                        try {
                            JsonGenerator gen = objectMapper.getFactory().createGenerator(gzip);
                            gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
                            try {
                                while (rs.next()) {
                                    gen.writeStartObject();
                                    gen.writeStringField("_t", typeTag);
                                    for (int i = 0; i < colCount; i++)
                                        writeField(gen, colNames[i], rs.getObject(i + 1));
                                    gen.writeEndObject();
                                    gen.writeRaw('\n');
                                    count[0]++;
                                }
                            } finally {
                                gen.flush();
                                gen.close();
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("GZIP/JSON stream error for " + typeTag, e);
                        }
                        return null;
                    }
            );
            return null;
        });
        return count[0];
    }

    private void writeField(JsonGenerator gen, String name, Object val) throws IOException {
        if (val == null) {
            gen.writeNullField(name);
        } else if (val instanceof PGobject pgObj) {
            gen.writeFieldName(name);
            String v = pgObj.getValue();
            if (v != null) gen.writeRawValue(v);
            else gen.writeNull();
        } else if (val instanceof Timestamp ts) {
            gen.writeNumberField(name, ts.getTime());
        } else if (val instanceof Long l) {
            gen.writeNumberField(name, l);
        } else if (val instanceof Integer i) {
            gen.writeNumberField(name, i);
        } else if (val instanceof Boolean b) {
            gen.writeBooleanField(name, b);
        } else if (val instanceof String s) {
            gen.writeStringField(name, s);
        } else {
            gen.writeObjectField(name, val);
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
            throw new RuntimeException("Invalid tenantId: " + tenantId, e);
        }
    }

    private String s3RegistryKey(LocalityDownsyncCriteria c, String fileType) {
        return c.getTenantId() + "/" + c.getLocality() + "/" + fileType + ".ndjson.gz";
    }

    private String s3ProjectKey(LocalityDownsyncCriteria c, String fileType) {
        String rootProjectId = c.getRootProjectId() != null ? c.getRootProjectId() : c.getProjectId();
        return c.getTenantId() + "/" + rootProjectId + "/" + c.getLocality() + "/" + fileType + ".ndjson.gz";
    }

    private void markFilesSkipped(String tenantId, String localityRowId) {
        for (String ft : REGISTRY_FILE_TYPES)
            jobRepository.updateFileCompleted(tenantId, localityRowId, ft, "SKIPPED",
                    null, null, null, null, System.currentTimeMillis());
    }

    private String truncate(String msg) {
        if (msg == null) return "unknown";
        return msg.length() > 2000 ? msg.substring(0, 2000) : msg;
    }

    private record FileResult(String fileType, boolean success, String s3Key,
                               long recordCount, String failureReason) {}
}
