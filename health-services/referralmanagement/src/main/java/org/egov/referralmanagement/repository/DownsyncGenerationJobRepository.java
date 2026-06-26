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
            " createdBy, createdTime, lastModifiedBy, lastModifiedTime, rowVersion, lastHeartbeat) " +
            "VALUES (:id, :tenantId, :projectId, :totalRequested, :totalSucceeded, :totalFailed, :status, " +
            " :createdBy, :createdTime, :lastModifiedBy, :lastModifiedTime, :rowVersion, :lastHeartbeat)";

    private static final String UPDATE_JOB =
            "UPDATE {schema}.downsync_generation_job " +
            "SET totalRequested=:totalRequested, totalSucceeded=:totalSucceeded, totalFailed=:totalFailed, " +
            "    status=:status, lastModifiedBy=:lastModifiedBy, lastModifiedTime=:lastModifiedTime, " +
            "    rowVersion=rowVersion+1 " +
            "WHERE id=:id";

    private static final String FIND_IN_PROGRESS_JOBS_IN_SCHEMA =
            "SELECT id, tenantId, projectId, createdBy, rowVersion, lastHeartbeat " +
            "FROM {schema}.downsync_generation_job WHERE status = 'IN_PROGRESS'";

    /**
     * Claim an abandoned IN_PROGRESS job. Two protections in one statement:
     *   • rowVersion CAS — defeats SIMULTANEOUS claims (only one of N pods
     *     reading the same snapshot wins).
     *   • lastHeartbeat freshness — defeats STAGGERED claims (a later-starting
     *     pod sees a fresh heartbeat from the live owner and gets rowsAffected=0).
     * Sets lastHeartbeat = :now atomically so the new owner is immediately
     * visible as "alive" to any other pod that races in next.
     */
    private static final String CLAIM_RESUME_JOB =
            "UPDATE {schema}.downsync_generation_job " +
            "SET rowVersion = rowVersion + 1, " +
            "    lastHeartbeat = :now, " +
            "    lastModifiedBy = 'system-resume', " +
            "    lastModifiedTime = :now " +
            "WHERE id = :id " +
            "  AND status = 'IN_PROGRESS' " +
            "  AND rowVersion = :expectedRowVersion " +
            "  AND (lastHeartbeat IS NULL OR lastHeartbeat < :staleThreshold)";

    /**
     * Periodic heartbeat from the owning pod. CAS by rowVersion — if another pod
     * has stolen ownership (rowVersion incremented), this returns 0 and the
     * heartbeat scheduler self-cancels.
     */
    private static final String BUMP_JOB_HEARTBEAT =
            "UPDATE {schema}.downsync_generation_job " +
            "SET lastHeartbeat = :now " +
            "WHERE id = :id AND rowVersion = :expectedRowVersion AND status = 'IN_PROGRESS'";

    /**
     * Sweep ALL IN_PROGRESS file rows under a job that we've just claimed and know
     * is abandoned (heartbeat was stale AND we won the CAS). No timestamp filter
     * needed — the job-level claim guarantees no live worker exists. Each swept
     * row becomes FAILED, and the attempt-per-row flow will INSERT a new attempt
     * for it on the next worker pass.
     */
    private static final String SWEEP_STALE_FILES_FOR_JOB =
            "UPDATE {schema}.downsync_locality_file " +
            "SET status = 'FAILED', " +
            "    endTime = :now, " +
            "    failureReason = 'abandoned: prior worker did not complete (pod restart, heartbeat stale)' " +
            "WHERE jobId = :jobId AND status = 'IN_PROGRESS'";

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
            // Multiple attempts may exist per (locality, fileType); pick the latest
            // successful one. attemptNumber is the source of truth for ordering across
            // attempts; endTime is a deterministic tiebreaker for rows backfilled to
            // attemptNumber=1.
            "ORDER BY f.fileType, f.attemptNumber DESC, f.endTime DESC";

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
            "(id, localityRowId, jobId, fileType, status, attemptNumber, createdTime) " +
            "VALUES (:id, :localityRowId, :jobId, :fileType, :status, 1, :createdTime)";

    /**
     * Claim a PENDING attempt row in place. STRICT CAS — PENDING only.
     *
     * Crash recovery is handled separately by SWEEP_STALE_FILES_FOR_JOB, which
     * marks abandoned IN_PROGRESS rows as FAILED at startup BEFORE workers run.
     * Once that sweep happens, an IN_PROGRESS row only ever means "another live
     * worker owns it" — never "crashed", so this claim must NOT match it.
     */
    private static final String CLAIM_FILE_ATTEMPT =
            "UPDATE {schema}.downsync_locality_file " +
            "SET status='IN_PROGRESS', startTime=:startTime, " +
            "    endTime=NULL, failureReason=NULL, s3Key=NULL, recordCount=NULL, filesize=NULL " +
            "WHERE id=:id AND status='PENDING'";

    /** INSERT a brand-new attempt row when retrying from a terminal-state row.
     *  attemptNumber = (max existing for same locality+fileType) + 1. */
    private static final String INSERT_RETRY_ATTEMPT =
            "INSERT INTO {schema}.downsync_locality_file " +
            "(id, localityRowId, jobId, fileType, status, startTime, attemptNumber, createdTime) " +
            "VALUES (:id, :localityRowId, :jobId, :fileType, 'IN_PROGRESS', :startTime, " +
            "  (SELECT COALESCE(MAX(attemptNumber), 0) + 1 FROM {schema}.downsync_locality_file " +
            "   WHERE localityRowId=:localityRowId AND fileType=:fileType), " +
            ":createdTime)";

    /** Finalize an in-flight row. CAS on status='IN_PROGRESS' — terminal-state rows are
     *  immutable, so this is a no-op if another writer beat us to it. */
    private static final String UPDATE_FILE_COMPLETED =
            "UPDATE {schema}.downsync_locality_file " +
            "SET status=:status, s3Key=:s3Key, recordCount=:recordCount, filesize=:fileSize, " +
            "    failureReason=:failureReason, endTime=:endTime " +
            "WHERE id=:id AND status='IN_PROGRESS'";

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

    /**
     * Returns the LATEST attempt per fileType, with that attempt's id + status.
     *
     * The caller decides how to start work based on the latest row's status:
     *   PENDING / IN_PROGRESS → claim the existing row in place (CLAIM_FILE_ATTEMPT).
     *   FAILED                → INSERT a new attempt row (INSERT_RETRY_ATTEMPT).
     *   SUCCESS / SKIPPED     → not returned at all; file is already done.
     *
     * Skipping the terminal states here keeps the SQL aligned with the invariant
     * "terminal-state rows are immutable" — we never even hand them to a worker.
     */
    private static final String FIND_RESUMABLE_FILE_TYPES =
            "SELECT DISTINCT ON (fileType) id, fileType, status " +
            "FROM {schema}.downsync_locality_file " +
            "WHERE localityRowId=:localityRowId " +
            "ORDER BY fileType, attemptNumber DESC";

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
                .addValue("rowVersion", job.getRowVersion())
                // Fresh job is owned by the creating pod from the moment of insert.
                // The caller starts the heartbeat scheduler immediately so no other
                // pod can grab the job during the gap between insert and first heartbeat.
                .addValue("lastHeartbeat", System.currentTimeMillis()));
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
     * Claim an abandoned IN_PROGRESS job. Two CAS conditions in one UPDATE:
     *   • rowVersion CAS — defeats simultaneous claims (multiple pods reading
     *     the same snapshot all attempt the UPDATE; exactly one matches)
     *   • lastHeartbeat freshness — defeats staggered claims (a later-starting
     *     pod sees the recorded heartbeat from the live owner and skips)
     * Sets lastHeartbeat = :now on success so the new owner is immediately
     * visible as "alive" to any other pod that races in next.
     *
     * @return true iff this pod won the claim (rowsAffected == 1).
     */
    public boolean claimResumeJob(String tenantId, String jobId, long expectedRowVersion,
                                   long staleHeartbeatThreshold) {
        int rows = jdbcTemplate.update(resolveSql(CLAIM_RESUME_JOB, tenantId),
                new MapSqlParameterSource()
                        .addValue("id", jobId)
                        .addValue("expectedRowVersion", expectedRowVersion)
                        .addValue("now", System.currentTimeMillis())
                        .addValue("staleThreshold", staleHeartbeatThreshold));
        return rows == 1;
    }

    /**
     * Bump lastHeartbeat for a job this pod owns. CAS by rowVersion — if another
     * pod has stolen ownership, rowsAffected=0 and the caller's heartbeat
     * scheduler should self-cancel.
     *
     * @return true iff the heartbeat write took effect (we still own the job).
     */
    public boolean bumpJobHeartbeat(String tenantId, String jobId, long expectedRowVersion) {
        int rows = jdbcTemplate.update(resolveSql(BUMP_JOB_HEARTBEAT, tenantId),
                new MapSqlParameterSource()
                        .addValue("id", jobId)
                        .addValue("expectedRowVersion", expectedRowVersion)
                        .addValue("now", System.currentTimeMillis()));
        return rows == 1;
    }

    /**
     * Mark all IN_PROGRESS file rows under this job as FAILED. Used by the
     * resume runner immediately after winning a job claim — at that point we
     * know no live worker exists for this job (job-level heartbeat was stale),
     * so any per-row IN_PROGRESS is genuinely abandoned. Each swept row stays
     * in the table as history; the attempt-per-row flow will INSERT a new
     * attempt for it on the next worker pass.
     *
     * @return number of rows swept.
     */
    public int sweepStaleFilesForJob(String tenantId, String jobId) {
        return jdbcTemplate.update(resolveSql(SWEEP_STALE_FILES_FOR_JOB, tenantId),
                new MapSqlParameterSource()
                        .addValue("jobId", jobId)
                        .addValue("now", System.currentTimeMillis()));
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
                    return jdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, i) -> {
                        long heartbeat = rs.getLong("lastHeartbeat");
                        return DownsyncGenerationJob.builder()
                                .id(rs.getString("id"))
                                .tenantId(rs.getString("tenantId"))
                                .projectId(rs.getString("projectId"))
                                .createdBy(rs.getString("createdBy"))
                                .rowVersion(rs.getLong("rowVersion"))
                                .lastHeartbeat(rs.wasNull() ? null : heartbeat)
                                .build();
                    }).stream();
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
                .addValue("status", file.getStatus())
                .addValue("createdTime", System.currentTimeMillis()));
    }

    /**
     * Claim a PENDING/IN_PROGRESS attempt row in place.
     * Returns true iff this caller won the claim (rowsAffected==1). A return of false
     * means the row is no longer in a claimable state — another worker or pod already
     * moved it on, OR it has reached a terminal state. Caller should skip silently.
     */
    public boolean claimFileAttempt(String tenantId, String id, long startTime) {
        int n = jdbcTemplate.update(resolveSql(CLAIM_FILE_ATTEMPT, tenantId), new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("startTime", startTime));
        return n == 1;
    }

    /**
     * INSERT a brand-new attempt row when retrying after a terminal state. Auto-increments
     * attemptNumber from the existing rows for the same (localityRowId, fileType). The
     * caller chooses the new row's id.
     */
    public void insertRetryAttempt(String tenantId, String newId, String localityRowId,
                                    String jobId, String fileType, long startTime) {
        jdbcTemplate.update(resolveSql(INSERT_RETRY_ATTEMPT, tenantId), new MapSqlParameterSource()
                .addValue("id", newId)
                .addValue("localityRowId", localityRowId)
                .addValue("jobId", jobId)
                .addValue("fileType", fileType)
                .addValue("startTime", startTime)
                .addValue("createdTime", System.currentTimeMillis()));
    }

    /**
     * Finalize an in-flight attempt by id. CAS — only updates if the row is still
     * IN_PROGRESS, making terminal-state rows immutable. Returns true on a successful
     * write, false if another writer already finalized this row (last-writer-loses).
     */
    public boolean updateFileCompleted(String tenantId, String id, String status,
                                        String s3Key, Long recordCount, Long fileSize,
                                        String failureReason, long endTime) {
        int n = jdbcTemplate.update(resolveSql(UPDATE_FILE_COMPLETED, tenantId), new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status)
                .addValue("s3Key", s3Key)
                .addValue("recordCount", recordCount)
                .addValue("fileSize", fileSize)
                .addValue("failureReason", failureReason)
                .addValue("endTime", endTime));
        return n == 1;
    }

    /**
     * Returns the latest attempt per fileType for a locality, with its id and status.
     * Terminal states (SUCCESS / SKIPPED) are filtered out by the caller, since they
     * don't need any further work. PENDING / IN_PROGRESS rows are claimed in place;
     * FAILED rows trigger a new attempt (insertRetryAttempt).
     */
    public List<ResumableFile> findResumableFileTypes(String tenantId, String localityRowId) {
        return jdbcTemplate.query(resolveSql(FIND_RESUMABLE_FILE_TYPES, tenantId),
                new MapSqlParameterSource("localityRowId", localityRowId),
                (rs, i) -> new ResumableFile(
                        rs.getString("id"),
                        rs.getString("fileType"),
                        rs.getString("status")));
    }

    /** DTO returned by {@link #findResumableFileTypes}. */
    public record ResumableFile(String id, String fileType, String status) {}

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
