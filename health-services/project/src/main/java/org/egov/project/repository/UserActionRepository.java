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
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.project.irs.UserAction;
import org.egov.common.models.project.irs.UserActionSearch;
import org.egov.common.producer.Producer;
import org.egov.common.utils.CommonUtils;
import org.egov.project.repository.rowmapper.UserActionRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.getIdMethod;

@Repository
@Slf4j
public class UserActionRepository extends GenericRepository<UserAction> {

    @Autowired
    protected UserActionRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                   RedisTemplate<String, Object> redisTemplate, SelectQueryBuilder selectQueryBuilder,
                                   UserActionRowMapper rowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, rowMapper, Optional.of("user_action"));
    }

    public SearchResponse<UserAction> find(UserActionSearch searchObject, URLParams urlParams) throws QueryBuilderException {
        String query = "SELECT id, clientreferenceid, tenantid, projectid, latitude, longitude, locationaccuracy, boundarycode, action, beneficiarytag, resourcetag, status, additionaldetails, createdby, createdtime, lastmodifiedby, lastmodifiedtime, clientcreatedtime, clientlastmodifiedtime, clientcreatedby, clientlastmodifiedby, rowversion FROM user_action ua";
        Map<String, Object> paramsMap = new HashMap<>();
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject,
                QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id IN (:id)", "ua.id IN (:id)");
        query = query.replace("clientReferenceId IN (:clientReferenceId)", "ua.clientReferenceId IN (:clientReferenceId)");

        if(CollectionUtils.isEmpty(whereFields)) {
            query = query + " where ua.tenantId=:tenantId ";
        } else {
            query = query + " and ua.tenantId=:tenantId ";
        }
        if (Boolean.FALSE.equals(urlParams.getIncludeDeleted())) {
            query = query + "and isDeleted=:isDeleted ";
        }

        if (urlParams.getLastChangedSince() != null) {
            query = query + "and lastModifiedTime>=:lastModifiedTime ";
        }
        paramsMap.put("tenantId", urlParams.getTenantId());
        paramsMap.put("isDeleted", urlParams.getIncludeDeleted());
        paramsMap.put("lastModifiedTime", urlParams.getLastChangedSince());

        Long totalCount = CommonUtils.constructTotalCountCTEAndReturnResult(query, paramsMap, this.namedParameterJdbcTemplate);

        query = query + "ORDER BY ua.id ASC LIMIT :limit OFFSET :offset";
        paramsMap.put("limit", urlParams.getLimit());
        paramsMap.put("offset", urlParams.getOffset());

        List<UserAction> userActionList = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);

        return SearchResponse.<UserAction>builder().response(userActionList).totalCount(totalCount).build();
    }

    

    public SearchResponse<UserAction> findById(List<String> ids, String columnName) {
        List<UserAction> objFound = findInCache(ids);
        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .collect(Collectors.toList()));
            if (ids.isEmpty()) {
                return SearchResponse.<UserAction>builder().response(objFound).build();
            }
        }

        String query = String.format("SELECT id, clientreferenceid, tenantid, projectid, latitude, longitude, locationaccuracy, boundarycode, action, beneficiarytag, resourcetag, status, additionaldetails, createdby, createdtime, lastmodifiedby, lastmodifiedtime, clientcreatedtime, clientlastmodifiedtime, clientcreatedby, clientlastmodifiedby, rowversion FROM user_action ua WHERE ua.%s IN (:ids) ", columnName);
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);
        List<UserAction> userActionList = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);

        objFound.addAll(userActionList);
        putInCache(objFound);
        return SearchResponse.<UserAction>builder().response(objFound).build();
    }
}
