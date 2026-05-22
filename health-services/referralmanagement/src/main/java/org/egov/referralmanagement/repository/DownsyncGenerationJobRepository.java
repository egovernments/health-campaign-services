package org.egov.referralmanagement.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.referralmanagement.web.models.DownsyncGenerationJob;
import org.egov.referralmanagement.web.models.DownsyncGenerationLocality;
import org.egov.referralmanagement.web.models.DownsyncJobDetail;
import org.egov.referralmanagement.web.models.DownsyncLocalityDetail;
import org.egov.referralmanagement.web.models.DownsyncLocalityFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class DownsyncGenerationJobRepository {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private MultiStateInstanceUtil multiStateInstanceUtil;

    // ── SQL templates — {schema} replaced at call time ────────────────────────

    private static final String INSERT_JOB =
            "INSERT INTO {schema}.downsync_generation_job " +
            "(id, tenantId, projectId, totalRequested, totalSucceeded, totalFailed, status, " +
            " createdBy, createdTime, lastModifiedBy, lastModifiedTime, rowVersion) " +
            "VALUES (:id, :tenantId, :projectId, :totalRequested, :totalSucceeded, :totalFailed, :status, " +
            " :createdBy, :createdTime, :lastModifiedBy, :lastModifiedTime, :rowVersion)";

    private static final String UPDATE_JOB =
            "UPDATE {schema}.downsync_generation_job " +
            "SET totalRequested=:totalRequested, totalSucceeded=:totalSucceeded, totalFailed=:totalFailed, " +
            "    status=:status, lastModifiedBy=:lastModifiedBy, lastModifiedTime=:lastModifiedTime, " +
            "    rowVersion=rowVersion+1 " +
            "WHERE id=:id";

    private static final String FIND_IN_PROGRESS_JOBS_IN_SCHEMA =
            "SELECT id, tenantId, projectId, createdBy, rowVersion FROM {schema}.downsync_generation_job " +
            "WHERE status = 'IN_PROGRESS'";

    private static final String CLAIM_RESUME_JOB =
            "UPDATE {schema}.downsync_generation_job " +
            "SET rowVersion = rowVersion + 1, lastModifiedBy = 'system-resume', lastModifiedTime = :now " +
            "WHERE id = :id AND status = 'IN_PROGRESS' AND rowVersion = :expectedRowVersion";

    private static final String HAS_IN_PROGRESS_JOB =
            "SELECT COUNT(*) FROM {schema}.downsync_generation_job " +
            "WHERE tenantId = :tenantId AND status = 'IN_PROGRESS'";

    private static final String FIND_JOB_BY_ID =
            "SELECT id, tenantId, projectId, totalRequested, totalSucceeded, totalFailed, " +
            "       status, createdBy, createdTime, lastModifiedBy, lastModifiedTime, rowVersion " +
            "FROM {schema}.downsync_generation_job WHERE id = :id";

    private static final String FIND_IN_PROGRESS_JOB_BY_TENANT =
            "SELECT id, tenantId, projectId, totalRequested, status, createdBy, createdTime " +
            "FROM {schema}.downsync_generation_job " +
            "WHERE tenantId = :tenantId AND status = 'IN_PROGRESS' " +
            "ORDER BY createdTime DESC LIMIT 1";

    private static final String COUNT_LOCALITIES_DONE =
            "SELECT COUNT(*) FROM {schema}.downsync_generation_locality " +
            "WHERE jobId = :jobId AND status IN ('SUCCESS','SKIPPED','PARTIAL_SUCCESS','FAILED')";

    private static final String COUNT_OUTCOMES =
            "SELECT " +
            "  COALESCE(SUM(CASE WHEN status IN ('SUCCESS','SKIPPED') THEN 1 ELSE 0 END), 0) AS succeeded, " +
            "  COALESCE(SUM(CASE WHEN status IN ('FAILED','PARTIAL_SUCCESS') THEN 1 ELSE 0 END), 0) AS failed " +
            "FROM {schema}.downsync_generation_locality WHERE jobId = :jobId";

    private static final String INSERT_LOCALITY =
            "INSERT INTO {schema}.downsync_generation_locality " +
            "(id, jobId, tenantId, projectId, locality, category, status, createdTime) " +
            "VALUES (:id, :jobId, :tenantId, :projectId, :locality, :category, :status, :createdTime)";

    private static final String UPDATE_LOCALITY_STARTED =
            "UPDATE {schema}.downsync_generation_locality SET status='IN_PROGRESS', startTime=:startTime " +
            "WHERE id=:id";

    private static final String UPDATE_LOCALITY_COMPLETED =
            "UPDATE {schema}.downsync_generation_locality " +
            "SET status=:status, failureReason=:failureReason, endTime=:endTime " +
            "WHERE id=:id";

    private static final String FIND_LATEST_FILES_FOR_LOCALITY =
            "SELECT DISTINCT ON (f.fileType) f.fileType, f.s3Key, f.recordCount " +
            "FROM {schema}.downsync_locality_file f " +
            "JOIN {schema}.downsync_generation_locality l ON l.id = f.localityRowId " +
            "JOIN {schema}.downsync_generation_job j ON j.id = l.jobId " +
            "WHERE l.tenantId = :tenantId " +
            "  AND l.locality = :locality " +
            "  AND ((:projectId IS NULL AND l.projectId IS NULL) OR l.projectId = :projectId) " +
            "  AND j.status IN ('COMPLETED','PARTIAL_FAILURE') " +
            "  AND f.status = 'SUCCESS' AND f.s3Key IS NOT NULL " +
            "ORDER BY f.fileType, f.endTime DESC";

    private static final String FIND_ALL_LOCALITIES_BY_JOB =
            "SELECT id, tenantId, projectId, locality, category, status, failureReason, " +
            "       startTime, endTime, createdTime " +
            "FROM {schema}.downsync_generation_locality WHERE jobId = :jobId ORDER BY createdTime";

    private static final String FIND_ALL_FILES_BY_JOB =
            "SELECT id, localityRowId, fileType, status, s3Key, recordCount, filesize, " +
            "       failureReason, startTime, endTime " +
            "FROM {schema}.downsync_locality_file WHERE jobId = :jobId ORDER BY localityRowId, fileType";

    private static final String FIND_RESUMABLE_LOCALITIES =
            "SELECT id, tenantId, projectId, locality, category FROM {schema}.downsync_generation_locality " +
            "WHERE jobId=:jobId AND status IN ('PENDING','IN_PROGRESS')";

    private static final String INSERT_FILE =
            "INSERT INTO {schema}.downsync_locality_file " +
            "(id, localityRowId, jobId, fileType, status) " +
            "VALUES (:id, :localityRowId, :jobId, :fileType, :status)";

    private static final String UPDATE_FILE_STARTED =
            "UPDATE {schema}.downsync_locality_file SET status='IN_PROGRESS', startTime=:startTime " +
            "WHERE localityRowId=:localityRowId AND fileType=:fileType";

    private static final String UPDATE_FILE_COMPLETED =
            "UPDATE {schema}.downsync_locality_file " +
            "SET status=:status, s3Key=:s3Key, recordCount=:recordCount, filesize=:fileSize, " +
            "    failureReason=:failureReason, endTime=:endTime " +
            "WHERE localityRowId=:localityRowId AND fileType=:fileType";

    // ── New queries for registry staleness and locality resolution ─────────────

    private static final String FETCH_ALL_LOCALITIES =
            "SELECT DISTINCT a.localitycode " +
            "FROM {schema}.household h " +
            "JOIN {schema}.address a ON (h.addressid)::text = (a.id)::text " +
            "WHERE h.tenantid = :tenantId AND h.isdeleted = false AND a.localitycode IS NOT NULL";

    private static final String FETCH_PROJECT_LOCALITY_MAPPING =
            "SELECT pa.projectid, pa.boundary AS locality " +
            "FROM {schema}.project_address pa " +
            "JOIN {schema}.project p ON p.id = pa.projectid " +
            "WHERE p.projecthierarchy LIKE '%' || :rootProjectId || '%' " +
            "  AND p.id != :rootProjectId " +
            "  AND pa.boundary IS NOT NULL";

    private static final String FIND_LATEST_FILE_END_TIME =
            "SELECT MAX(f.endTime) " +
            "FROM {schema}.downsync_locality_file f " +
            "JOIN {schema}.downsync_generation_locality l ON l.id = f.localityRowId " +
            "JOIN {schema}.downsync_generation_job j ON j.id = l.jobId " +
            "WHERE l.tenantId = :tenantId " +
            "  AND l.locality = :locality " +
            "  AND l.projectId IS NULL " +
            "  AND j.status IN ('COMPLETED','PARTIAL_FAILURE') " +
            "  AND f.fileType = :fileType AND f.status = 'SUCCESS'";

    private static final String FIND_MAX_HOUSEHOLD_MODIFIED_TIME =
            "SELECT MAX(lastmodifiedtime) FROM {schema}.household_address_mv " +
            "WHERE localitycode = :locality AND tenantid = :tenantId AND isdeleted = false";

    private static final String FIND_MAX_HH_MEMBER_MODIFIED_TIME =
            "SELECT MAX(hm.lastmodifiedtime) " +
            "FROM {schema}.HOUSEHOLD_MEMBER hm " +
            "JOIN {schema}.household h ON (hm.householdClientReferenceId)::text = (h.clientReferenceId)::text " +
            "JOIN {schema}.address a ON (h.addressid)::text = (a.id)::text " +
            "WHERE a.localitycode = :locality AND h.tenantid = :tenantId " +
            "  AND h.isdeleted = false AND hm.isDeleted = false";

    private static final String FIND_MAX_INDIVIDUAL_MODIFIED_TIME =
            "SELECT MAX(ind.lastmodifiedtime) " +
            "FROM {schema}.individual ind " +
            "JOIN {schema}.HOUSEHOLD_MEMBER hm " +
            "  ON (ind.clientReferenceId)::text = (hm.individualClientReferenceId)::text " +
            "JOIN {schema}.household h ON (hm.householdClientReferenceId)::text = (h.clientReferenceId)::text " +
            "JOIN {schema}.address a ON (h.addressid)::text = (a.id)::text " +
            "WHERE a.localitycode = :locality AND h.tenantid = :tenantId " +
            "  AND h.isdeleted = false AND hm.isDeleted = false AND ind.isDeleted = false";

    // ── Project file staleness queries ────────────────────────────────────────

    private static final String FIND_LATEST_PROJECT_FILE_END_TIME =
            "SELECT MAX(f.endTime) " +
            "FROM {schema}.downsync_locality_file f " +
            "JOIN {schema}.downsync_generation_locality l ON l.id = f.localityRowId " +
            "JOIN {schema}.downsync_generation_job j ON j.id = l.jobId " +
            "WHERE l.tenantId = :tenantId " +
            "  AND l.locality = :locality " +
            "  AND l.projectId = :projectId " +
            "  AND j.status IN ('COMPLETED','PARTIAL_FAILURE') " +
            "  AND f.fileType = :fileType AND f.status = 'SUCCESS'";

    // Reusable subquery: all beneficiary clientReferenceIds in a given project+locality
    private static final String BENE_LOCALITY_SUBQUERY =
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

    private static final String FIND_MAX_BENEFICIARY_MODIFIED_TIME =
            "SELECT MAX(pb.lastmodifiedtime) FROM {schema}.PROJECT_BENEFICIARY pb " +
            "WHERE pb.projectId = :projectId AND pb.isDeleted = false " +
            "AND pb.beneficiaryClientReferenceId IN (" +
            "  SELECT clientReferenceId FROM {schema}.household_address_mv " +
            "  WHERE localitycode = :locality AND isdeleted = false " +
            "  UNION " +
            "  SELECT hm.individualClientReferenceId FROM {schema}.HOUSEHOLD_MEMBER hm " +
            "  WHERE hm.isDeleted = false " +
            "  AND hm.householdClientReferenceId IN (" +
            "    SELECT clientReferenceId FROM {schema}.household_address_mv " +
            "    WHERE localitycode = :locality AND isdeleted = false" +
            "  )" +
            ")";

    private static final String FIND_MAX_SIDE_EFFECT_MODIFIED_TIME =
            "SELECT MAX(se.lastmodifiedtime) FROM {schema}.SIDE_EFFECT se " +
            "WHERE se.isDeleted = false " +
            "AND se.projectBeneficiaryClientReferenceId IN (" + BENE_LOCALITY_SUBQUERY + ")";

    private static final String FIND_MAX_REFERRAL_MODIFIED_TIME =
            "SELECT MAX(r.lastmodifiedtime) FROM {schema}.REFERRAL r " +
            "WHERE r.projectid = :projectId AND r.isDeleted = false " +
            "AND r.projectBeneficiaryClientReferenceId IN (" + BENE_LOCALITY_SUBQUERY + ")";

    private static final String FIND_MAX_HF_REFERRAL_MODIFIED_TIME =
            "SELECT MAX(hfr.lastmodifiedtime) FROM {schema}.HF_REFERRAL hfr " +
            "WHERE hfr.projectid = :projectId AND hfr.localitycode = :locality AND hfr.isdeleted = false";

    private static final String FIND_MAX_TASK_MODIFIED_TIME =
            "SELECT MAX(pt.lastmodifiedtime) FROM {schema}.PROJECT_TASK pt " +
            "WHERE pt.projectId = :projectId AND pt.isDeleted = false " +
            "AND pt.projectBeneficiaryClientReferenceId IN (" + BENE_LOCALITY_SUBQUERY + ")";

    private static final String REFRESH_HOUSEHOLD_ADDRESS_MV =
            "REFRESH MATERIALIZED VIEW CONCURRENTLY {schema}.household_address_mv";

    private static final String FIND_MAX_HOUSEHOLD_MODIFIED_TIME_TENANT =
            "SELECT MAX(lastmodifiedtime) FROM {schema}.household WHERE tenantid = :tenantId";

    private static final String FIND_LAST_COMPLETED_JOB_TIME =
            "SELECT MAX(createdTime) FROM {schema}.downsync_generation_job " +
            "WHERE tenantId = :tenantId AND status IN ('COMPLETED','PARTIAL_FAILURE')";

    private static final String FIND_LEAF_PROJECT_ID_FOR_LOCALITY =
            "SELECT pa.projectid " +
            "FROM {schema}.project_address pa " +
            "JOIN {schema}.project p ON p.id = pa.projectid " +
            "WHERE p.projecthierarchy LIKE '%' || :rootProjectId || '%' " +
            "  AND p.id != :rootProjectId " +
            "  AND pa.boundary = :locality " +
            "LIMIT 1";

    private static final String FIND_RESUMABLE_FILE_TYPES =
            "SELECT fileType FROM {schema}.downsync_locality_file " +
            "WHERE localityRowId=:localityRowId AND status IN ('PENDING','IN_PROGRESS','FAILED')";

    // ── Schema resolution ─────────────────────────────────────────────────────

    private String resolveSql(String template, String tenantId) {
        try {
            return multiStateInstanceUtil.replaceSchemaPlaceholder(template, tenantId);
        } catch (InvalidTenantIdException e) {
            throw new RuntimeException("Cannot resolve schema for tenantId: " + tenantId, e);
        }
    }

    /** Discovers all schemas that contain the downsync audit tables. Used by startup scanner. */
    private List<String> auditTableSchemas() {
        return jdbcTemplate.queryForList(
                "SELECT table_schema FROM information_schema.tables " +
                "WHERE table_name = 'downsync_generation_job' ORDER BY table_schema",
                new MapSqlParameterSource(), String.class);
    }

    // ── Public methods — Job ──────────────────────────────────────────────────

    public void insertJob(DownsyncGenerationJob job) {
        jdbcTemplate.update(resolveSql(INSERT_JOB, job.getTenantId()), new MapSqlParameterSource()
                .addValue("id", job.getId())
                .addValue("tenantId", job.getTenantId())
                .addValue("projectId", job.getProjectId())
                .addValue("totalRequested", job.getTotalRequested())
                .addValue("totalSucceeded", job.getTotalSucceeded())
                .addValue("totalFailed", job.getTotalFailed())
                .addValue("status", job.getStatus())
                .addValue("createdBy", job.getCreatedBy())
                .addValue("createdTime", job.getCreatedTime())
                .addValue("lastModifiedBy", job.getLastModifiedBy())
                .addValue("lastModifiedTime", job.getLastModifiedTime())
                .addValue("rowVersion", job.getRowVersion()));
    }

    public void updateJob(DownsyncGenerationJob job) {
        jdbcTemplate.update(resolveSql(UPDATE_JOB, job.getTenantId()), new MapSqlParameterSource()
                .addValue("id", job.getId())
                .addValue("totalRequested", job.getTotalRequested())
                .addValue("totalSucceeded", job.getTotalSucceeded())
                .addValue("totalFailed", job.getTotalFailed())
                .addValue("status", job.getStatus())
                .addValue("lastModifiedBy", job.getLastModifiedBy())
                .addValue("lastModifiedTime", job.getLastModifiedTime()));
    }

    /**
     * Atomically claims a resume job using optimistic locking on rowVersion.
     * Returns true only if this pod wins the race (rowsAffected == 1).
     * If another pod already claimed it, rowVersion will have changed and this returns false.
     */
    public boolean claimResumeJob(String tenantId, String jobId, long expectedRowVersion) {
        int rows = jdbcTemplate.update(resolveSql(CLAIM_RESUME_JOB, tenantId),
                new MapSqlParameterSource()
                        .addValue("id", jobId)
                        .addValue("expectedRowVersion", expectedRowVersion)
                        .addValue("now", System.currentTimeMillis()));
        return rows == 1;
    }

    public boolean hasInProgressJob(String tenantId) {
        Integer count = jdbcTemplate.queryForObject(resolveSql(HAS_IN_PROGRESS_JOB, tenantId),
                new MapSqlParameterSource("tenantId", tenantId), Integer.class);
        return count != null && count > 0;
    }

    /**
     * Scans all schemas that have the audit table — used only at startup to resume
     * interrupted jobs. Schemas are discovered dynamically from information_schema.
     */
    public List<DownsyncGenerationJob> findInProgressJobs() {
        return auditTableSchemas().stream()
                .flatMap(schema -> {
                    String sql = FIND_IN_PROGRESS_JOBS_IN_SCHEMA.replace("{schema}", schema);
                    return jdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, i) ->
                            DownsyncGenerationJob.builder()
                                    .id(rs.getString("id"))
                                    .tenantId(rs.getString("tenantId"))
                                    .projectId(rs.getString("projectId"))
                                    .createdBy(rs.getString("createdBy"))
                                    .rowVersion(rs.getLong("rowVersion"))
                                    .build()).stream();
                })
                .toList();
    }

    public Map<String, Integer> countOutcomes(String tenantId, String jobId) {
        return jdbcTemplate.queryForMap(resolveSql(COUNT_OUTCOMES, tenantId),
                new MapSqlParameterSource("jobId", jobId))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> ((Number) e.getValue()).intValue()));
    }

    // ── Public methods — Locality ─────────────────────────────────────────────

    public void insertLocality(DownsyncGenerationLocality locality) {
        jdbcTemplate.update(resolveSql(INSERT_LOCALITY, locality.getTenantId()), new MapSqlParameterSource()
                .addValue("id", locality.getId())
                .addValue("jobId", locality.getJobId())
                .addValue("tenantId", locality.getTenantId())
                .addValue("projectId", locality.getProjectId())
                .addValue("locality", locality.getLocality())
                .addValue("category", locality.getCategory())
                .addValue("status", locality.getStatus())
                .addValue("createdTime", locality.getCreatedTime()));
    }

    public void updateLocalityStarted(String tenantId, String localityRowId, long startTime) {
        jdbcTemplate.update(resolveSql(UPDATE_LOCALITY_STARTED, tenantId), new MapSqlParameterSource()
                .addValue("id", localityRowId)
                .addValue("startTime", startTime));
    }

    public void updateLocalityCompleted(String tenantId, String localityRowId, String status,
                                         String failureReason, long endTime) {
        jdbcTemplate.update(resolveSql(UPDATE_LOCALITY_COMPLETED, tenantId), new MapSqlParameterSource()
                .addValue("id", localityRowId)
                .addValue("status", status)
                .addValue("failureReason", failureReason)
                .addValue("endTime", endTime));
    }

    public DownsyncGenerationJob findJobById(String jobId) {
        for (String schema : auditTableSchemas()) {
            String sql = FIND_JOB_BY_ID.replace("{schema}", schema);
            List<DownsyncGenerationJob> rows = jdbcTemplate.query(sql,
                    new MapSqlParameterSource("id", jobId), (rs, i) ->
                            DownsyncGenerationJob.builder()
                                    .id(rs.getString("id"))
                                    .tenantId(rs.getString("tenantId"))
                                    .projectId(rs.getString("projectId"))
                                    .totalRequested(rs.getInt("totalRequested"))
                                    .totalSucceeded(rs.getInt("totalSucceeded"))
                                    .totalFailed(rs.getInt("totalFailed"))
                                    .status(rs.getString("status"))
                                    .createdBy(rs.getString("createdBy"))
                                    .createdTime(rs.getLong("createdTime"))
                                    .lastModifiedBy(rs.getString("lastModifiedBy"))
                                    .lastModifiedTime(rs.getLong("lastModifiedTime"))
                                    .rowVersion(rs.getLong("rowVersion"))
                                    .build());
            if (!rows.isEmpty()) return rows.get(0);
        }
        return null;
    }

    public DownsyncGenerationJob findInProgressJobByTenant(String tenantId) {
        List<DownsyncGenerationJob> rows = jdbcTemplate.query(
                resolveSql(FIND_IN_PROGRESS_JOB_BY_TENANT, tenantId),
                new MapSqlParameterSource("tenantId", tenantId), (rs, i) ->
                        DownsyncGenerationJob.builder()
                                .id(rs.getString("id"))
                                .tenantId(rs.getString("tenantId"))
                                .projectId(rs.getString("projectId"))
                                .totalRequested(rs.getInt("totalRequested"))
                                .status(rs.getString("status"))
                                .createdBy(rs.getString("createdBy"))
                                .createdTime(rs.getLong("createdTime"))
                                .build());
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int countLocalitiesDone(String tenantId, String jobId) {
        Integer count = jdbcTemplate.queryForObject(resolveSql(COUNT_LOCALITIES_DONE, tenantId),
                new MapSqlParameterSource("jobId", jobId), Integer.class);
        return count != null ? count : 0;
    }

    /** Fetches job + all localities + all files in 3 queries and assembles the nested detail. */
    public DownsyncJobDetail findJobDetail(String jobId) {
        DownsyncGenerationJob job = findJobById(jobId);
        if (job == null) return null;

        String tenantId = job.getTenantId();
        List<DownsyncLocalityFile> allFiles = findAllFilesByJob(tenantId, jobId);
        Map<String, List<DownsyncLocalityFile>> filesByLocality = allFiles.stream()
                .collect(Collectors.groupingBy(DownsyncLocalityFile::getLocalityRowId));

        List<DownsyncLocalityDetail> localityDetails = findAllLocalitiesByJob(tenantId, jobId).stream()
                .map(l -> DownsyncLocalityDetail.builder()
                        .id(l.getId())
                        .locality(l.getLocality())
                        .tenantId(l.getTenantId())
                        .projectId(l.getProjectId())
                        .category(l.getCategory())
                        .status(l.getStatus())
                        .failureReason(l.getFailureReason())
                        .startTime(l.getStartTime())
                        .endTime(l.getEndTime())
                        .createdTime(l.getCreatedTime())
                        .files(filesByLocality.getOrDefault(l.getId(), List.of()))
                        .build())
                .toList();

        return DownsyncJobDetail.builder()
                .id(job.getId())
                .tenantId(job.getTenantId())
                .projectId(job.getProjectId())
                .totalRequested(job.getTotalRequested())
                .totalSucceeded(job.getTotalSucceeded())
                .totalFailed(job.getTotalFailed())
                .status(job.getStatus())
                .createdBy(job.getCreatedBy())
                .createdTime(job.getCreatedTime())
                .lastModifiedBy(job.getLastModifiedBy())
                .lastModifiedTime(job.getLastModifiedTime())
                .rowVersion(job.getRowVersion())
                .localities(localityDetails)
                .build();
    }

    public List<DownsyncLocalityFile> findLatestFilesForLocality(String tenantId, String projectId,
                                                                   String locality) {
        return jdbcTemplate.query(resolveSql(FIND_LATEST_FILES_FOR_LOCALITY, tenantId),
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("projectId", projectId, Types.VARCHAR)
                        .addValue("locality", locality),
                (rs, i) -> DownsyncLocalityFile.builder()
                        .fileType(rs.getString("fileType"))
                        .s3Key(rs.getString("s3Key"))
                        .recordCount(rs.getObject("recordCount", Long.class))
                        .build());
    }

    public List<DownsyncGenerationLocality> findAllLocalitiesByJob(String tenantId, String jobId) {
        return jdbcTemplate.query(resolveSql(FIND_ALL_LOCALITIES_BY_JOB, tenantId),
                new MapSqlParameterSource("jobId", jobId), (rs, i) ->
                        DownsyncGenerationLocality.builder()
                                .id(rs.getString("id"))
                                .jobId(jobId)
                                .tenantId(rs.getString("tenantId"))
                                .projectId(rs.getString("projectId"))
                                .locality(rs.getString("locality"))
                                .category(rs.getString("category"))
                                .status(rs.getString("status"))
                                .failureReason(rs.getString("failureReason"))
                                .startTime(rs.getObject("startTime", Long.class))
                                .endTime(rs.getObject("endTime", Long.class))
                                .createdTime(rs.getObject("createdTime", Long.class))
                                .build());
    }

    public List<DownsyncLocalityFile> findAllFilesByJob(String tenantId, String jobId) {
        return jdbcTemplate.query(resolveSql(FIND_ALL_FILES_BY_JOB, tenantId),
                new MapSqlParameterSource("jobId", jobId), (rs, i) ->
                        DownsyncLocalityFile.builder()
                                .id(rs.getString("id"))
                                .localityRowId(rs.getString("localityRowId"))
                                .jobId(jobId)
                                .fileType(rs.getString("fileType"))
                                .status(rs.getString("status"))
                                .s3Key(rs.getString("s3Key"))
                                .recordCount(rs.getObject("recordCount", Long.class))
                                .fileSize(rs.getObject("filesize", Long.class))
                                .failureReason(rs.getString("failureReason"))
                                .startTime(rs.getObject("startTime", Long.class))
                                .endTime(rs.getObject("endTime", Long.class))
                                .build());
    }

    public List<DownsyncGenerationLocality> findResumableLocalities(String tenantId, String jobId) {
        return jdbcTemplate.query(resolveSql(FIND_RESUMABLE_LOCALITIES, tenantId),
                new MapSqlParameterSource("jobId", jobId), (rs, i) ->
                        DownsyncGenerationLocality.builder()
                                .id(rs.getString("id"))
                                .tenantId(rs.getString("tenantId"))
                                .projectId(rs.getString("projectId"))
                                .locality(rs.getString("locality"))
                                .category(rs.getString("category"))
                                .build());
    }

    // ── Public methods — File ─────────────────────────────────────────────────

    public void insertFile(String tenantId, DownsyncLocalityFile file) {
        jdbcTemplate.update(resolveSql(INSERT_FILE, tenantId), new MapSqlParameterSource()
                .addValue("id", file.getId())
                .addValue("localityRowId", file.getLocalityRowId())
                .addValue("jobId", file.getJobId())
                .addValue("fileType", file.getFileType())
                .addValue("status", file.getStatus()));
    }

    public void updateFileStarted(String tenantId, String localityRowId, String fileType, long startTime) {
        jdbcTemplate.update(resolveSql(UPDATE_FILE_STARTED, tenantId), new MapSqlParameterSource()
                .addValue("localityRowId", localityRowId)
                .addValue("fileType", fileType)
                .addValue("startTime", startTime));
    }

    public void updateFileCompleted(String tenantId, String localityRowId, String fileType, String status,
                                     String s3Key, Long recordCount, Long fileSize,
                                     String failureReason, long endTime) {
        jdbcTemplate.update(resolveSql(UPDATE_FILE_COMPLETED, tenantId), new MapSqlParameterSource()
                .addValue("localityRowId", localityRowId)
                .addValue("fileType", fileType)
                .addValue("status", status)
                .addValue("s3Key", s3Key)
                .addValue("recordCount", recordCount)
                .addValue("fileSize", fileSize)
                .addValue("failureReason", failureReason)
                .addValue("endTime", endTime));
    }

    public List<String> findResumableFileTypes(String tenantId, String localityRowId) {
        return jdbcTemplate.queryForList(resolveSql(FIND_RESUMABLE_FILE_TYPES, tenantId),
                new MapSqlParameterSource("localityRowId", localityRowId), String.class);
    }

    // ── New queries — locality resolution and registry staleness ─────────────

    public List<String> fetchAllLocalities(String tenantId) {
        return jdbcTemplate.queryForList(resolveSql(FETCH_ALL_LOCALITIES, tenantId),
                new MapSqlParameterSource("tenantId", tenantId), String.class);
    }

    /** Returns list of [projectId, locality] pairs for all leaf projects under rootProjectId. */
    public List<String[]> fetchProjectLocalityMapping(String tenantId, String rootProjectId) {
        return jdbcTemplate.query(resolveSql(FETCH_PROJECT_LOCALITY_MAPPING, tenantId),
                new MapSqlParameterSource("rootProjectId", rootProjectId),
                (rs, i) -> new String[]{rs.getString("projectid"), rs.getString("locality")});
    }

    /** Returns the endTime of the last successful generation of fileType for this locality, or null. */
    public Long findLatestFileEndTime(String tenantId, String locality, String fileType) {
        return jdbcTemplate.queryForObject(resolveSql(FIND_LATEST_FILE_END_TIME, tenantId),
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("locality", locality)
                        .addValue("fileType", fileType),
                Long.class);
    }

    /** Refreshes household_address_mv for the given tenant schema using CONCURRENTLY (non-blocking). */
    public void refreshHouseholdAddressMv(String tenantId) {
        jdbcTemplate.getJdbcTemplate().execute(resolveSql(REFRESH_HOUSEHOLD_ADDRESS_MV, tenantId));
    }

    /**
     * Returns true if household_address_mv should be refreshed before this job.
     * Compares MAX(household.lastmodifiedtime) against the last completed job's createdTime.
     * Defaults to true on any error so the job never proceeds with a potentially stale MV.
     */
    public boolean shouldRefreshMv(String tenantId) {
        try {
            Long lastJobTime = jdbcTemplate.queryForObject(
                    resolveSql(FIND_LAST_COMPLETED_JOB_TIME, tenantId),
                    new MapSqlParameterSource("tenantId", tenantId), Long.class);

            if (lastJobTime == null) return true;

            Long householdMax = jdbcTemplate.queryForObject(
                    resolveSql(FIND_MAX_HOUSEHOLD_MODIFIED_TIME_TENANT, tenantId),
                    new MapSqlParameterSource("tenantId", tenantId), Long.class);

            if (householdMax == null) return false;

            return householdMax > lastJobTime;
        } catch (Exception e) {
            log.warn("MV refresh check failed for tenant={}, defaulting to refresh. Cause: {}",
                    tenantId, e.getMessage());
            return true;
        }
    }

    /** Returns MAX(lastmodifiedtime) from household_address_mv for the locality, or null. */
    public Long findMaxHouseholdModifiedTime(String tenantId, String locality) {
        return jdbcTemplate.queryForObject(resolveSql(FIND_MAX_HOUSEHOLD_MODIFIED_TIME, tenantId),
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("locality", locality),
                Long.class);
    }

    /** Returns MAX(lastmodifiedtime) from HOUSEHOLD_MEMBER for the locality, or null. */
    public Long findMaxHhMemberModifiedTime(String tenantId, String locality) {
        return jdbcTemplate.queryForObject(resolveSql(FIND_MAX_HH_MEMBER_MODIFIED_TIME, tenantId),
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("locality", locality),
                Long.class);
    }

    /** Returns MAX(lastmodifiedtime) from individual for the locality, or null. */
    public Long findMaxIndividualModifiedTime(String tenantId, String locality) {
        return jdbcTemplate.queryForObject(resolveSql(FIND_MAX_INDIVIDUAL_MODIFIED_TIME, tenantId),
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("locality", locality),
                Long.class);
    }

    /** Returns the endTime of the last successful generation of fileType for this project locality, or null. */
    public Long findLatestProjectFileEndTime(String tenantId, String locality, String projectId, String fileType) {
        return jdbcTemplate.queryForObject(resolveSql(FIND_LATEST_PROJECT_FILE_END_TIME, tenantId),
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("locality", locality)
                        .addValue("projectId", projectId)
                        .addValue("fileType", fileType),
                Long.class);
    }

    public Long findMaxBeneficiaryModifiedTime(String tenantId, String locality, String projectId) {
        return jdbcTemplate.queryForObject(resolveSql(FIND_MAX_BENEFICIARY_MODIFIED_TIME, tenantId),
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId).addValue("locality", locality).addValue("projectId", projectId),
                Long.class);
    }

    public Long findMaxSideEffectModifiedTime(String tenantId, String locality, String projectId) {
        return jdbcTemplate.queryForObject(resolveSql(FIND_MAX_SIDE_EFFECT_MODIFIED_TIME, tenantId),
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId).addValue("locality", locality).addValue("projectId", projectId),
                Long.class);
    }

    public Long findMaxReferralModifiedTime(String tenantId, String locality, String projectId) {
        return jdbcTemplate.queryForObject(resolveSql(FIND_MAX_REFERRAL_MODIFIED_TIME, tenantId),
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId).addValue("locality", locality).addValue("projectId", projectId),
                Long.class);
    }

    public Long findMaxHfReferralModifiedTime(String tenantId, String locality, String projectId) {
        return jdbcTemplate.queryForObject(resolveSql(FIND_MAX_HF_REFERRAL_MODIFIED_TIME, tenantId),
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId).addValue("locality", locality).addValue("projectId", projectId),
                Long.class);
    }

    public Long findMaxTaskModifiedTime(String tenantId, String locality, String projectId) {
        return jdbcTemplate.queryForObject(resolveSql(FIND_MAX_TASK_MODIFIED_TIME, tenantId),
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId).addValue("locality", locality).addValue("projectId", projectId),
                Long.class);
    }

    /** Finds the leaf projectId for a given (rootProjectId, locality) pair. Used by DownsyncPregenService. */
    public String findLeafProjectIdForLocality(String tenantId, String rootProjectId, String locality) {
        List<String> rows = jdbcTemplate.queryForList(
                resolveSql(FIND_LEAF_PROJECT_ID_FOR_LOCALITY, tenantId),
                new MapSqlParameterSource()
                        .addValue("rootProjectId", rootProjectId)
                        .addValue("locality", locality),
                String.class);
        return rows.isEmpty() ? null : rows.get(0);
    }

}
