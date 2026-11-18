package org.egov.stock.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.stock.Stock;
import org.egov.common.producer.Producer;
import org.egov.stock.repository.rowmapper.StockRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.egov.common.utils.CommonUtils.constructTotalCountCTEAndReturnResult;
import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

@Repository
public class StockRepository extends GenericRepository<Stock> {
    @Autowired
    public StockRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                             RedisTemplate<String, Object> redisTemplate,
                             SelectQueryBuilder selectQueryBuilder, StockRowMapper stockRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                stockRowMapper, Optional.of("stock"));
    }

    public SearchResponse<Stock> findWithCount(Object searchObject,
                                               Integer limit,
                                               Integer offset,
                                               String tenantId,
                                               Long lastChangedSince,
                                               Boolean includeDeleted,
                                               Set<String> allowedFacilityIds) throws QueryBuilderException, InvalidTenantIdException {
        Map<String, Object> paramsMap = new HashMap<>();
        String query = selectQueryBuilder.build(searchObject, tableName, SCHEMA_REPLACE_STRING, paramsMap);
        query += " AND tenantId=:tenantId ";
        if (query.contains(tableName + " AND")) {
            query = query.replace(tableName + " AND", tableName + " WHERE");
        }
        query += "AND (senderId IN (:allowedFacilityIds) OR receiverId IN (:allowedFacilityIds)) ";
        if (Boolean.FALSE.equals(includeDeleted)) {
            query += "AND isDeleted=:isDeleted ";
        }
        if (lastChangedSince != null) {
            query += "AND lastModifiedTime>=:lastModifiedTime ";
        }
        query += "ORDER BY id ASC";
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        paramsMap.put("allowedFacilityIds", allowedFacilityIds);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramsMap, namedParameterJdbcTemplate);

        query += " LIMIT :limit OFFSET :offset";
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);

        return SearchResponse.<Stock>builder()
                .response(namedParameterJdbcTemplate.query(query, paramsMap, rowMapper))
                .totalCount(totalCount)
                .build();
    }
}
