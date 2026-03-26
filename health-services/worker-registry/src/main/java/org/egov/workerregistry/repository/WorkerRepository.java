package org.egov.workerregistry.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.workerregistry.web.models.Worker;
import org.egov.workerregistry.web.models.WorkerSearch;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

@Repository
@Slf4j
public class WorkerRepository {

    private static final String TABLE_NAME = "eg_hcm_worker_registry";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final MultiStateInstanceUtil multiStateInstanceUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public WorkerRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                            MultiStateInstanceUtil multiStateInstanceUtil,
                            RedisTemplate<String, Object> redisTemplate,
                            ObjectMapper objectMapper) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.multiStateInstanceUtil = multiStateInstanceUtil;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Worker> find(WorkerSearch criteria) throws InvalidTenantIdException {
        MapSqlParameterSource paramMap = new MapSqlParameterSource();
        String query = buildSearchQuery(criteria, paramMap);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, criteria.getTenantId());
        return namedParameterJdbcTemplate.query(query, paramMap, new WorkerRowMapper());
    }

    public void save(List<Worker> workers, String tenantId) {
        String query = String.format(
                "INSERT INTO %s.%s (id, tenantId, name, payeePhoneNumber, paymentProvider, payeeName, " +
                        "bankAccount, bankCode, photoId, signatureId, additionalDetails, isDeleted, " +
                        "createdBy, lastModifiedBy, createdTime, lastModifiedTime, rowVersion) " +
                        "VALUES (:id, :tenantId, :name, :payeePhoneNumber, :paymentProvider, :payeeName, " +
                        ":bankAccount, :bankCode, :photoId, :signatureId, :additionalDetails, :isDeleted, " +
                        ":createdBy, :lastModifiedBy, :createdTime, :lastModifiedTime, :rowVersion)",
                SCHEMA_REPLACE_STRING, TABLE_NAME);

        try {
            query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        } catch (InvalidTenantIdException e) {
            throw new RuntimeException("Invalid tenant id: " + tenantId, e);
        }

        SqlParameterSource[] params = workers.stream()
                .map(this::buildInsertParams)
                .toArray(SqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(query, params);
        log.info("Saved {} workers to DB", workers.size());
    }

    public void update(List<Worker> workers, String tenantId) {
        String query = String.format(
                "UPDATE %s.%s SET name = :name, payeePhoneNumber = :payeePhoneNumber, " +
                        "paymentProvider = :paymentProvider, payeeName = :payeeName, " +
                        "bankAccount = :bankAccount, bankCode = :bankCode, " +
                        "photoId = :photoId, signatureId = :signatureId, " +
                        "additionalDetails = :additionalDetails, isDeleted = :isDeleted, " +
                        "lastModifiedBy = :lastModifiedBy, lastModifiedTime = :lastModifiedTime, " +
                        "rowVersion = :rowVersion WHERE id = :id AND isDeleted = false",
                SCHEMA_REPLACE_STRING, TABLE_NAME);

        try {
            query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        } catch (InvalidTenantIdException e) {
            throw new RuntimeException("Invalid tenant id: " + tenantId, e);
        }

        SqlParameterSource[] params = workers.stream()
                .map(this::buildUpdateParams)
                .toArray(SqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(query, params);
        log.info("Updated {} workers in DB", workers.size());
    }

    public void putInCache(List<Worker> workers) {
        if (workers == null || workers.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> workerMap = workers.stream()
                    .collect(Collectors.toMap(Worker::getId, w -> w, (w1, w2) -> w2));
            redisTemplate.opsForHash().putAll(TABLE_NAME, workerMap);
            log.info("Cached {} workers", workers.size());
        } catch (Exception e) {
            log.warn("Error while saving workers to cache", e);
        }
    }

    private MapSqlParameterSource buildInsertParams(Worker worker) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", worker.getId());
        params.addValue("tenantId", worker.getTenantId());
        params.addValue("name", worker.getName());
        params.addValue("payeePhoneNumber", worker.getPayeePhoneNumber());
        params.addValue("paymentProvider", worker.getPaymentProvider());
        params.addValue("payeeName", worker.getPayeeName());
        params.addValue("bankAccount", worker.getBankAccount());
        params.addValue("bankCode", worker.getBankCode());
        params.addValue("photoId", worker.getPhotoId());
        params.addValue("signatureId", worker.getSignatureId());
        params.addValue("additionalDetails", toJsonbPGobject(worker.getAdditionalDetails()));
        params.addValue("isDeleted", worker.getIsDeleted());
        params.addValue("createdBy", worker.getAuditDetails().getCreatedBy());
        params.addValue("lastModifiedBy", worker.getAuditDetails().getLastModifiedBy());
        params.addValue("createdTime", worker.getAuditDetails().getCreatedTime());
        params.addValue("lastModifiedTime", worker.getAuditDetails().getLastModifiedTime());
        params.addValue("rowVersion", worker.getRowVersion());
        return params;
    }

    private MapSqlParameterSource buildUpdateParams(Worker worker) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", worker.getId());
        params.addValue("name", worker.getName());
        params.addValue("payeePhoneNumber", worker.getPayeePhoneNumber());
        params.addValue("paymentProvider", worker.getPaymentProvider());
        params.addValue("payeeName", worker.getPayeeName());
        params.addValue("bankAccount", worker.getBankAccount());
        params.addValue("bankCode", worker.getBankCode());
        params.addValue("photoId", worker.getPhotoId());
        params.addValue("signatureId", worker.getSignatureId());
        params.addValue("additionalDetails", toJsonbPGobject(worker.getAdditionalDetails()));
        params.addValue("isDeleted", worker.getIsDeleted());
        params.addValue("lastModifiedBy", worker.getAuditDetails().getLastModifiedBy());
        params.addValue("lastModifiedTime", worker.getAuditDetails().getLastModifiedTime());
        params.addValue("rowVersion", worker.getRowVersion());
        return params;
    }

    private PGobject toJsonbPGobject(Object obj) {
        if (obj == null) return null;
        try {
            PGobject pgObject = new PGobject();
            pgObject.setType("jsonb");
            pgObject.setValue(objectMapper.writeValueAsString(obj));
            return pgObject;
        } catch (JsonProcessingException | SQLException e) {
            throw new RuntimeException("Error converting to JSONB", e);
        }
    }

    private String buildSearchQuery(WorkerSearch criteria, MapSqlParameterSource paramMap) {
        StringBuilder query = new StringBuilder(
                String.format("SELECT * FROM %s.%s WHERE tenantId = :tenantId AND isDeleted = false",
                        SCHEMA_REPLACE_STRING, TABLE_NAME));
        paramMap.addValue("tenantId", criteria.getTenantId());

        if (criteria.getId() != null && !criteria.getId().isEmpty()) {
            query.append(" AND id IN (:ids)");
            paramMap.addValue("ids", criteria.getId());
        }

        if (criteria.getName() != null && !criteria.getName().isEmpty()) {
            query.append(" AND name = :name");
            paramMap.addValue("name", criteria.getName());
        }

        query.append(" ORDER BY createdTime DESC");
        return query.toString();
    }

    public static class WorkerRowMapper implements RowMapper<Worker> {
        @Override
        public Worker mapRow(ResultSet rs, int rowNum) throws SQLException {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(rs.getString("createdBy"))
                    .lastModifiedBy(rs.getString("lastModifiedBy"))
                    .createdTime(rs.getLong("createdTime"))
                    .lastModifiedTime(rs.getLong("lastModifiedTime"))
                    .build();

            return Worker.builder()
                    .id(rs.getString("id"))
                    .tenantId(rs.getString("tenantId"))
                    .name(rs.getString("name"))
                    .payeePhoneNumber(rs.getString("payeePhoneNumber"))
                    .paymentProvider(rs.getString("paymentProvider"))
                    .payeeName(rs.getString("payeeName"))
                    .bankAccount(rs.getString("bankAccount"))
                    .bankCode(rs.getString("bankCode"))
                    .photoId(rs.getString("photoId"))
                    .signatureId(rs.getString("signatureId"))
                    .additionalDetails(rs.getObject("additionalDetails"))
                    .isDeleted(rs.getBoolean("isDeleted"))
                    .rowVersion(rs.getInt("rowVersion"))
                    .auditDetails(auditDetails)
                    .build();
        }
    }
}
