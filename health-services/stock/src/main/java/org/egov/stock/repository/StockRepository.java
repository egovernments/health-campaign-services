package org.egov.stock.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockSearch;
import org.egov.common.models.stock.TransactionType;
import org.egov.common.producer.Producer;
import org.egov.stock.repository.rowmapper.StockRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.egov.common.utils.CommonUtils.constructTotalCountCTEAndReturnResult;

@Repository
public class StockRepository extends GenericRepository<Stock> {
    @Autowired
    public StockRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            RedisTemplate<String, Object> redisTemplate,
            SelectQueryBuilder selectQueryBuilder, StockRowMapper stockRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                stockRowMapper, Optional.of("stock"));
    }

    public SearchResponse<Stock> findStock(StockSearch searchObject,
            Integer limit,
            Integer offset,
            String tenantId,
            Long lastChangedSince,
            Boolean includeDeleted) throws QueryBuilderException, InvalidTenantIdException {
        String query = selectQueryBuilder.build(searchObject, tableName);
        query += " AND tenantId=:tenantId ";
        if (query.contains(tableName + " AND")) {
            query = query.replace(tableName + " AND", tableName + " WHERE");
        }
        if (Boolean.FALSE.equals(includeDeleted)) {
            query += "AND isDeleted=:isDeleted ";
        }
        if (lastChangedSince != null) {
            query += "AND lastModifiedTime>=:lastModifiedTime ";
        }
        if (searchObject.getTransactionType() != null && !searchObject.getTransactionType().isEmpty()
                && !query.contains("transactionType")) {
            query += "AND transactionType IN (:transactionType) ";
        }
        query += "ORDER BY id ASC";
        Map<String, Object> paramsMap = selectQueryBuilder.getParamsMap();
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);

        if (searchObject.getTransactionType() != null && !searchObject.getTransactionType().isEmpty()
                && !paramsMap.containsKey("transactionType")) {
            List<String> transactionTypeStringValues = new ArrayList<>();
            for (TransactionType transactionType : searchObject.getTransactionType()) {
                transactionTypeStringValues.add(transactionType.toString());
            }
            paramsMap.put("transactionType", transactionTypeStringValues);
        }
        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramsMap, namedParameterJdbcTemplate);

        query += " LIMIT :limit OFFSET :offset";
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);

        List<Stock> resultantList = namedParameterJdbcTemplate.query(query, paramsMap, rowMapper);

        return SearchResponse.<Stock>builder().response(resultantList).totalCount(totalCount).build();
    }
}