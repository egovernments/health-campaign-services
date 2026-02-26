package org.egov.workerregistry.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.workerregistry.web.models.Worker;
import org.egov.workerregistry.web.models.WorkerIndividualMap;
import org.egov.workerregistry.web.models.WorkerSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

@Repository
@Slf4j
public class WorkerIndividualMapRepository {

    private static final String TABLE_NAME = "eg_hcm_worker_individual_map";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final MultiStateInstanceUtil multiStateInstanceUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public WorkerIndividualMapRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                         MultiStateInstanceUtil multiStateInstanceUtil, RedisTemplate<String, Object> redisTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.multiStateInstanceUtil = multiStateInstanceUtil;
        this.redisTemplate = redisTemplate;
    }

    public List<String> findWorkerIdsByIndividualIds(List<String> individualIds, String tenantId) throws InvalidTenantIdException {
        String query = String.format(
                "SELECT workerId FROM %s.%s WHERE individualId IN (:individualIds) AND tenantId = :tenantId AND isDeleted = false",
                SCHEMA_REPLACE_STRING, TABLE_NAME);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);

        MapSqlParameterSource paramMap = new MapSqlParameterSource();
        paramMap.addValue("individualIds", individualIds);
        paramMap.addValue("tenantId", tenantId);

        return namedParameterJdbcTemplate.queryForList(query, paramMap, String.class);
    }

    public List<WorkerIndividualMap> findIndividualIdsByWorkerIds(List<String> workerIds, String tenantId) throws InvalidTenantIdException {
        String query = String.format(
                "SELECT * FROM %s.%s WHERE workerId IN (:workerIds) AND tenantId = :tenantId AND isDeleted = false",
                SCHEMA_REPLACE_STRING, TABLE_NAME);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);

        MapSqlParameterSource paramMap = new MapSqlParameterSource();
        paramMap.addValue("workerIds", workerIds);
        paramMap.addValue("tenantId", tenantId);

        return namedParameterJdbcTemplate.query(query, paramMap, (rs, rowNum) ->
                WorkerIndividualMap.builder()
                        .id(rs.getString("id"))
                        .workerId(rs.getString("workerId"))
                        .individualId(rs.getString("individualId"))
                        .tenantId(rs.getString("tenantId"))
                        .isDeleted(rs.getBoolean("isDeleted"))
                        .build());
    }

    public List<String> findExistingIndividualIds(List<String> individualIds, String tenantId) throws InvalidTenantIdException {
        String query = String.format(
                "SELECT individualId FROM %s.%s WHERE individualId IN (:individualIds) AND tenantId = :tenantId AND isDeleted = false",
                SCHEMA_REPLACE_STRING, TABLE_NAME);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);

        MapSqlParameterSource paramMap = new MapSqlParameterSource();
        paramMap.addValue("individualIds", individualIds);
        paramMap.addValue("tenantId", tenantId);

        return namedParameterJdbcTemplate.queryForList(query, paramMap, String.class);
    }

    public void save(List<WorkerIndividualMap> maps, String tenantId) {
        String query = String.format(
                "INSERT INTO %s.%s (id, workerId, individualId, tenantId, isDeleted, " +
                        "createdBy, lastModifiedBy, createdTime, lastModifiedTime) " +
                        "VALUES (:id, :workerId, :individualId, :tenantId, :isDeleted, " +
                        ":createdBy, :lastModifiedBy, :createdTime, :lastModifiedTime)",
                SCHEMA_REPLACE_STRING, TABLE_NAME);

        try {
            query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        } catch (InvalidTenantIdException e) {
            throw new RuntimeException("Invalid tenant id: " + tenantId, e);
        }

        SqlParameterSource[] params = maps.stream()
                .map(this::buildInsertParams)
                .toArray(SqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(query, params);
        log.info("Saved {} worker-individual maps to DB", maps.size());
    }

    public void putInCache(List<WorkerIndividualMap> workerMaps) {
        if (workerMaps == null || workerMaps.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> workerMap = workerMaps.stream()
                    .collect(Collectors.toMap(WorkerIndividualMap::getIndividualId, w -> w, (w1, w2) -> w2));
            redisTemplate.opsForHash().putAll(TABLE_NAME, workerMap);
            log.info("Cached {} individual workers", workerMaps.size());
        } catch (Exception e) {
            log.warn("Error while saving workers to cache", e);
        }
    }

    private MapSqlParameterSource buildInsertParams(WorkerIndividualMap map) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", map.getId());
        params.addValue("workerId", map.getWorkerId());
        params.addValue("individualId", map.getIndividualId());
        params.addValue("tenantId", map.getTenantId());
        params.addValue("isDeleted", map.getIsDeleted());
        params.addValue("createdBy", map.getAuditDetails().getCreatedBy());
        params.addValue("lastModifiedBy", map.getAuditDetails().getLastModifiedBy());
        params.addValue("createdTime", map.getAuditDetails().getCreatedTime());
        params.addValue("lastModifiedTime", map.getAuditDetails().getLastModifiedTime());
        return params;
    }

    public List<WorkerIndividualMap> find(List<String> individualIds, String tenantId) throws InvalidTenantIdException {
        MapSqlParameterSource paramMap = new MapSqlParameterSource();
        String query = String.format(
                "SELECT * FROM %s.%s WHERE individualId IN (:individualIds) AND tenantId = :tenantId AND isDeleted = false",
                SCHEMA_REPLACE_STRING, TABLE_NAME);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);

        paramMap.addValue("individualIds", individualIds);
        paramMap.addValue("tenantId", tenantId);

        return namedParameterJdbcTemplate.query(query, paramMap, new WorkerIndividualRowMapper());
    }

    public List<WorkerIndividualMap> findByWorkerIds(List<String> workerIds, String tenantId) throws InvalidTenantIdException {
        MapSqlParameterSource paramMap = new MapSqlParameterSource();
        String query = String.format(
                "SELECT * FROM %s.%s WHERE workerId IN (:workerIds) AND tenantId = :tenantId AND isDeleted = false",
                SCHEMA_REPLACE_STRING, TABLE_NAME);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);

        paramMap.addValue("workerIds", workerIds);
        paramMap.addValue("tenantId", tenantId);

        return namedParameterJdbcTemplate.query(query, paramMap, new WorkerIndividualRowMapper());
    }

    public static class WorkerIndividualRowMapper implements RowMapper<WorkerIndividualMap> {
        @Override
        public WorkerIndividualMap mapRow(ResultSet rs, int rowNum) throws SQLException {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(rs.getString("createdBy"))
                    .lastModifiedBy(rs.getString("lastModifiedBy"))
                    .createdTime(rs.getLong("createdTime"))
                    .lastModifiedTime(rs.getLong("lastModifiedTime"))
                    .build();

            return WorkerIndividualMap.builder()
                    .id(rs.getString("id"))
                    .tenantId(rs.getString("tenantId"))
                    .individualId(rs.getString("individualId"))
                    .workerId(rs.getString("workerId"))
                    .isDeleted(rs.getBoolean("isDeleted"))
                    .auditDetails(auditDetails)
                    .build();
        }
    }
}
