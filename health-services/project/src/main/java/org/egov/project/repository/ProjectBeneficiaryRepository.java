package org.egov.project.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.producer.Producer;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.project.repository.rowmapper.ProjectBeneficiaryRowMapper;
import org.egov.common.models.project.ProjectBeneficiarySearch;
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

import static org.egov.common.utils.CommonUtils.constructTotalCountCTEAndReturnResult;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

@Repository
@Slf4j
public class ProjectBeneficiaryRepository extends GenericRepository<ProjectBeneficiary> {

    @Autowired
    public ProjectBeneficiaryRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                           RedisTemplate<String, Object> redisTemplate,
                                           SelectQueryBuilder selectQueryBuilder, ProjectBeneficiaryRowMapper projectBeneficiaryRowMapper,
                                           MultiStateInstanceUtil multiStateInstanceUtil) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                projectBeneficiaryRowMapper, Optional.of("project_beneficiary"));
    }

    public SearchResponse<ProjectBeneficiary> find(ProjectBeneficiarySearch householdMemberSearch,
                                                Integer limit,
                                                Integer offset,
                                                String tenantId,
                                                Long lastChangedSince,
                                                Boolean includeDeleted) throws InvalidTenantIdException {

        Map<String, Object> paramsMap = new HashMap<>();
        StringBuilder queryBuilder = new StringBuilder();

        String query = String.format("SELECT * FROM %s.project_beneficiary ", SCHEMA_REPLACE_STRING);

        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(householdMemberSearch, QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString().trim();

        query = query + " AND tenantId=:tenantId ";

        if (query.contains(this.tableName + " AND")) {
            query = query.replace(this.tableName + " AND", this.tableName + " WHERE");
        }

        queryBuilder.append(query);

        if (Boolean.FALSE.equals(includeDeleted)) {
            queryBuilder.append("AND isDeleted=:isDeleted ");
        }

        if (lastChangedSince != null) {
            queryBuilder.append("AND lastModifiedTime>=:lastModifiedTime ");
        }

        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);

        queryBuilder.append(" ORDER BY id ASC ");
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(queryBuilder.toString(), tenantId);
        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramsMap, this.namedParameterJdbcTemplate);

        query += " LIMIT :limit OFFSET :offset";
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);

        List<ProjectBeneficiary> projectBeneficiaries = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);

        return SearchResponse.<ProjectBeneficiary>builder().totalCount(totalCount).response(projectBeneficiaries).build();
    }

    public SearchResponse<ProjectBeneficiary> findById(String tenantId, List<String> ids, String columnName, Boolean includeDeleted) throws InvalidTenantIdException {
        List<ProjectBeneficiary> objFound = findInCache(tenantId, ids);
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
                log.info("all objects were found in the cache, returning objects");
                return SearchResponse.<ProjectBeneficiary>builder().response(objFound).build();
            }
        }

        String query = String.format("SELECT * FROM %s.project_beneficiary where %s IN (:ids) AND isDeleted = false", SCHEMA_REPLACE_STRING, columnName);
        if (null != includeDeleted && includeDeleted) {
            query = String.format("SELECT * FROM %s.project_beneficiary WHERE %s IN (:ids)", SCHEMA_REPLACE_STRING, columnName);
        }
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("ids", ids);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);

        objFound.addAll(this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper));
        putInCache(objFound);
        log.info("returning objects from the database");
        return SearchResponse.<ProjectBeneficiary>builder().response(objFound).build();
    }
}

