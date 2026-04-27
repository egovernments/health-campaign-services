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
            "SELECT id, tenantId, projectId, createdBy FROM {schema}.downsync_generation_job " +
            "WHERE status = 'IN_PROGRESS'";

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
            "WHERE jobId = :jobId AND status IN ('SUCCESS','PARTIAL_SUCCESS','FAILED')";

    private static final String COUNT_OUTCOMES =
            "SELECT " +
            "  COALESCE(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS succeeded, " +
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
            "SELECT f.fileType, f.s3Key, f.recordCount " +
            "FROM {schema}.downsync_locality_file f " +
            "WHERE f.localityRowId = ( " +
            "  SELECT l.id FROM {schema}.downsync_generation_locality l " +
            "  INNER JOIN {schema}.downsync_generation_job j ON j.id = l.jobId " +
            "  WHERE l.tenantId = :tenantId " +
            "    AND l.locality = :locality " +
            "    AND ((:projectId IS NULL AND l.projectId IS NULL) OR l.projectId = :projectId) " +
            "    AND l.status IN ('SUCCESS','PARTIAL_SUCCESS') " +
            "    AND j.status IN ('COMPLETED','PARTIAL_FAILURE') " +
            "  ORDER BY l.createdTime DESC LIMIT 1 " +
            ") " +
            "AND f.status = 'SUCCESS' AND f.s3Key IS NOT NULL";

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
            "SELECT DISTINCT localitycode FROM {schema}.household_address_mv " +
            "WHERE tenantid = :tenantId AND isdeleted = false AND localitycode IS NOT NULL";

    private static final String FETCH_PROJECT_LOCALITY_MAPPING =
            "SELECT pa.projectid, pa.boundary AS locality " +
            "FROM {schema}.project_address pa " +
            "JOIN {schema}.project p ON p.id = pa.projectid " +
            "WHERE p.projecthierarchy LIKE '%' || :rootProjectId || '%' " +
            "  AND p.id != :rootProjectId " +
            "  AND pa.boundary IS NOT NULL";

    private static final String FIND_LATEST_REGISTRY_FILE_END_TIME =
            "SELECT MAX(f.endTime) " +
            "FROM {schema}.downsync_locality_file f " +
            "WHERE f.localityRowId = ( " +
            "  SELECT l.id FROM {schema}.downsync_generation_locality l " +
            "  INNER JOIN {schema}.downsync_generation_job j ON j.id = l.jobId " +
            "  WHERE l.tenantId = :tenantId " +
            "    AND l.locality = :locality " +
            "    AND l.projectId IS NULL " +
            "    AND l.status IN ('SUCCESS','PARTIAL_SUCCESS') " +
            "    AND j.status IN ('COMPLETED','PARTIAL_FAILURE') " +
            "  ORDER BY l.createdTime DESC LIMIT 1 " +
            ") " +
            "AND f.fileType = 'HH_MEMBERS' AND f.status = 'SUCCESS'";

    private static final String FIND_MAX_HOUSEHOLD_MODIFIED_TIME =
            "SELECT MAX(lastmodifiedtime) FROM {schema}.household_address_mv " +
            "WHERE localitycode = :locality AND tenantid = :tenantId AND isdeleted = false";

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

    /** Returns the endTime of the last successful HH_MEMBERS file for this locality, or null. */
    public Long findLatestRegistryFileEndTime(String tenantId, String locality) {
        return jdbcTemplate.queryForObject(resolveSql(FIND_LATEST_REGISTRY_FILE_END_TIME, tenantId),
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("locality", locality),
                Long.class);
    }

    /** Returns MAX(lastmodifiedtime) from household_address_mv for locality, or null. */
    public Long findMaxHouseholdModifiedTime(String tenantId, String locality) {
        return jdbcTemplate.queryForObject(resolveSql(FIND_MAX_HOUSEHOLD_MODIFIED_TIME, tenantId),
                new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("locality", locality),
                Long.class);
    }

    /** Finds the leaf projectId for a given (rootProjectId, locality) pair. */
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
