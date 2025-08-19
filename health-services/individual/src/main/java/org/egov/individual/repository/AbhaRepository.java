package org.egov.individual.repository;

import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.individual.AbhaTransaction;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.producer.Producer;
import org.egov.common.utils.CommonUtils;
import org.egov.individual.repository.rowmapper.*;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;
import static org.egov.individual.Constants.INVALID_TENANT_ID;
import static org.egov.individual.Constants.INVALID_TENANT_ID_MSG;

import org.springframework.dao.DataAccessException;
@Repository
@Slf4j
public class AbhaRepository extends GenericRepository<AbhaTransaction> {

    private static final String TABLE_NAME = "abha_individual_transaction";

    public AbhaRepository(@Qualifier("abhaTransactionProducer") Producer producer,
                             NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                             RedisTemplate<String, Object> redisTemplate,
                             @Qualifier("abhaTxnRowMapper") AbhaRowMapper rowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate,
                null, rowMapper, Optional.of(TABLE_NAME));
    }


    public SearchResponse<AbhaTransaction> findByIndividualId(String individualId, String tenantId) {
        String query = String.format("SELECT * FROM %s.%s WHERE individualId = :individualId AND isDeleted = false ORDER BY lastModifiedTime DESC LIMIT 1", SCHEMA_REPLACE_STRING, TABLE_NAME);

        try {
            query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        } catch (InvalidTenantIdException e) {
            throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("individualId", individualId);

        try {
            List<AbhaTransaction> results = this.namedParameterJdbcTemplate.query(query, params, this.rowMapper);
            return SearchResponse.<AbhaTransaction>builder()
                    .totalCount((long) results.size())
                    .response(results)
                    .build();
        } catch (DataAccessException e) {
            log.error("Error querying AbhaTransaction by individualId", e);
            return SearchResponse.<AbhaTransaction>builder()
                    .totalCount(0L)
                    .response(Collections.emptyList())
                    .build();
        }
    }

    public void deleteByIndividualId(String individualId, String tenantId, String modifiedBy, String transacitonId, String abhaNumber) {
        String query = String.format(
                "UPDATE %s.%s SET isDeleted = true, lastModifiedTime = :modifiedTime, lastModifiedBy = :modifiedBy, transactionId= :transactionId, abhaNumber = :abhaNumber WHERE individualId = :individualId",
                SCHEMA_REPLACE_STRING, TABLE_NAME
        );

        try {
            query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        } catch (InvalidTenantIdException e) {
            throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("individualId", individualId);
        params.put("modifiedTime", System.currentTimeMillis());
        params.put("modifiedBy", modifiedBy);
        params.put("transactionId", transacitonId);
        params.put("abhaNumber", abhaNumber);


        try {
            this.namedParameterJdbcTemplate.update(query, params);
            log.info("Soft deleted AbhaTxn for individualId: {}", individualId);
        } catch (DataAccessException e) {
            log.error("Failed to soft delete AbhaTxn for individualId: {}", individualId, e);
        }
    }

    public Optional<String> findActiveAbhaNumberByIndividualId(String individualId, String tenantId) {
        String query = String.format("SELECT abhaNumber FROM %s.%s WHERE individualId = :individualId ", SCHEMA_REPLACE_STRING, TABLE_NAME);

        try {
            query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        } catch (InvalidTenantIdException e) {
            throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
        }

        Map<String, Object> params = Map.of("individualId", individualId);
        try {
            List<String> results = this.namedParameterJdbcTemplate.query(query, params, (rs, rowNum) -> rs.getString("abhaNumber"));
            return results.isEmpty() ? Optional.empty() : Optional.ofNullable(results.get(0));
        } catch (DataAccessException e) {
            log.error("Error querying abhaNumber by individualId", e);
            throw new CustomException("DB_ERROR", "Error fetching ABHA number");
        }
    }


}

