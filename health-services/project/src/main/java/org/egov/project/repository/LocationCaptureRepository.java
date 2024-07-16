package org.egov.project.repository;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.project.irs.LocationCapture;
import org.egov.common.models.project.irs.LocationCaptureSearch;
import org.egov.common.producer.Producer;
import org.egov.common.utils.CommonUtils;
import org.egov.project.repository.rowmapper.LocationCaptureRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.getIdMethod;

/**
 *  TODO add columns in select queries and update the row mapper accordingly
 */

@Repository
@Slf4j
public class LocationCaptureRepository extends GenericRepository<LocationCapture> {

    @Autowired
    protected LocationCaptureRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate, RedisTemplate<String, Object> redisTemplate, SelectQueryBuilder selectQueryBuilder, LocationCaptureRowMapper locationCaptureRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, locationCaptureRowMapper, Optional.of("LOCATION_CAPTURE"));
    }

    public SearchResponse<LocationCapture> find(LocationCaptureSearch searchObject, URLParams urlParams) {
        String query = "SELECT * FROM location_capture lc ";
        Map<String, Object> paramsMap = new HashMap<>();
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject,QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id IN (:id)", "lc.id IN (:id)");
        query = query.replace("clientReferenceId IN (:clientReferenceId)", "lc.clientReferenceId IN (:clientReferenceId)");

        if(CollectionUtils.isEmpty(whereFields)) {
            query = query + " where lc.tenantId=:tenantId ";
        } else {
            query = query + " and lc.tenantId=:tenantId ";
        }

        if (urlParams.getLastChangedSince() != null) {
            query = query + "and lc.lastModifiedTime>=:lastModifiedTime ";
        }
        paramsMap.put("tenantId", urlParams.getTenantId());
        paramsMap.put("lastModifiedTime", urlParams.getLastChangedSince());

        Long totalCount = CommonUtils.constructTotalCountCTEAndReturnResult(query, paramsMap, this.namedParameterJdbcTemplate);

        query = query + "ORDER BY lc.id ASC LIMIT :limit OFFSET :offset";
        paramsMap.put("limit", urlParams.getLimit());
        paramsMap.put("offset", urlParams.getOffset());

        List<LocationCapture> locationCaptureList = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);

        return SearchResponse.<LocationCapture>builder().response(locationCaptureList).totalCount(totalCount).build();
    }

    public SearchResponse<LocationCapture> findById(List<String> ids, String columnName) {
        List<LocationCapture> objFound = findInCache(ids);
        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .collect(Collectors.toList()));
            if (ids.isEmpty()) {
                return SearchResponse.<LocationCapture>builder().response(objFound).build();
            }
        }

        String query = String.format("SELECT * FROM location_capture lc WHERE lc.%s IN (:ids)", columnName);

        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);
        List<LocationCapture> locationCaptureList = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);

        objFound.addAll(locationCaptureList);
        putInCache(objFound);
        return SearchResponse.<LocationCapture>builder().response(objFound).build();
    }
}
