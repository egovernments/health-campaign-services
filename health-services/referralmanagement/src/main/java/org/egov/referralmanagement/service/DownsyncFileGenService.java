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
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
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
                            throw new RuntimeException("Stream error for " + typeTag, e);
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
            default -> throw new IllegalArgumentException("Unknown type tag: " + typeTag);
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
