package org.egov.household.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.ds.Tuple;
import org.egov.common.models.household.Household;
import org.egov.common.producer.Producer;
import org.egov.household.repository.rowmapper.HouseholdRowMapper;
import org.egov.household.web.models.HouseholdSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdMethod;

@Repository
@Slf4j
public class HouseholdRepository extends GenericRepository<Household> {

    @Autowired
    protected HouseholdRepository(Producer producer,
                                  NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                  RedisTemplate<String, Object> redisTemplate,
                                  SelectQueryBuilder selectQueryBuilder,
                                  HouseholdRowMapper householdRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, householdRowMapper, Optional.of("household"));
    }

    public Tuple<Long, List<Household>> findById(List<String> ids, String columnName, Boolean includeDeleted) {
        List<Household> objFound = findInCache(ids);
        if (!includeDeleted) {
            objFound = objFound.stream()
                    .filter(entity -> entity.getIsDeleted().equals(false))
                    .collect(Collectors.toList());
        }
        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .collect(Collectors.toList()));
            if (ids.isEmpty()) {
                return new Tuple<>(Long.valueOf(objFound.size()), objFound);
            }
        }

        String query = String.format("SELECT *, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid FROM household h LEFT JOIN address a ON h.addressid = a.id WHERE h.%s IN (:ids) AND isDeleted = false", columnName);
        if (null != includeDeleted && includeDeleted) {
            query = String.format("SELECT *, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid FROM household h LEFT JOIN address a ON h.addressid = a.id  WHERE h.%s IN (:ids)", columnName);
        }
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("ids", ids);

        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramMap);

        objFound.addAll(this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper));
        putInCache(objFound);
        return new Tuple<>(totalCount, objFound);
    }

    public Tuple<Long, List<Household>> find(HouseholdSearch searchObject, Integer limit, Integer offset, String tenantId, Long lastChangedSince, Boolean includeDeleted) throws QueryBuilderException {
        String query = "SELECT h.*, a.*, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid";
        if (searchObject.getLocalityCode() != null) {
            query += " FROM (SELECT * FROM address WHERE localitycode = '" + searchObject.getLocalityCode() + "') a LEFT JOIN household h ON a.id = h.addressid";
        } else {
            query += " FROM household h LEFT JOIN address a ON h.addressid = a.id";
        }

        Map<String, Object> paramsMap = new HashMap<>();
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject, QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id IN (:id)", "h.id IN (:id)");
        query = query.replace("clientReferenceId IN (:clientReferenceId)", "h.clientReferenceId IN (:clientReferenceId)");

        query = query + " and h.tenantId=:tenantId ";
        if (Boolean.FALSE.equals(includeDeleted)) {
            query = query + "and isDeleted=:isDeleted ";
        }

        if (lastChangedSince != null) {
            query = query + "and lastModifiedTime>=:lastModifiedTime ";
        }

        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);

        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramsMap);

        query = query + "ORDER BY h.lastModifiedTime ASC LIMIT :limit OFFSET :offset";
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        return new Tuple<>(totalCount, this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper));
    }

    private Long constructTotalCountCTEAndReturnResult(String query, Map<String, Object> paramsMap) {
        String cteQuery = "WITH result_cte AS ("+query+"), totalCount_cte AS (SELECT COUNT(*) AS totalRows FROM result_cte) select * from totalCount_cte";
        return this.namedParameterJdbcTemplate.query(cteQuery, paramsMap, resultSet -> {
            if(resultSet.next())
                return resultSet.getLong("totalRows");
            else
                return 0L;
        });
    }


}
