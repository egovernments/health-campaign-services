package org.egov.household.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.Relationship;
import org.egov.common.models.household.HouseholdMemberSearch;
import org.egov.common.producer.Producer;
import org.egov.household.repository.rowmapper.RelationshipRowMapper;
import org.egov.household.repository.rowmapper.HouseholdMemberRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.constructTotalCountCTEAndReturnResult;
import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getIdMethod;

@Repository
@Slf4j
public class HouseholdMemberRepository extends GenericRepository<HouseholdMember> {

    @Autowired
    private RelationshipRowMapper relationshipRowMapper;

    @Autowired
    protected HouseholdMemberRepository(Producer producer,
                                        NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                        RedisTemplate<String, Object> redisTemplate,
                                        SelectQueryBuilder selectQueryBuilder,
                                        HouseholdMemberRowMapper householdMemberRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, householdMemberRowMapper, Optional.of("household_member"));
    }


    public SearchResponse<HouseholdMember> find(HouseholdMemberSearch householdMemberSearch,
                                                Integer limit,
                                                Integer offset,
                                                String tenantId,
                                                Long lastChangedSince,
                                                Boolean includeDeleted) {

        Map<String, Object> paramsMap = new HashMap<>();
        StringBuilder queryBuilder = new StringBuilder();

        String query = "SELECT * FROM household_member";

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

        Long totalCount = constructTotalCountCTEAndReturnResult(queryBuilder.toString(), paramsMap, this.namedParameterJdbcTemplate);

        queryBuilder.append(" LIMIT :limit OFFSET :offset");
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);

        List<HouseholdMember> householdMembers = this.namedParameterJdbcTemplate.query(queryBuilder.toString(), paramsMap, this.rowMapper);
        fetchAndSetHouseholdMemberRelationship(householdMembers, includeDeleted);

        return SearchResponse.<HouseholdMember>builder().totalCount(totalCount).response(householdMembers).build();
    }

    public SearchResponse<HouseholdMember> findById(List<String> ids, String columnName, Boolean includeDeleted) {
        List<HouseholdMember> objFound = findInCache(ids).stream()
                .filter(entity -> entity.getIsDeleted().equals(includeDeleted))
                .collect(Collectors.toList());
        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .collect(Collectors.toList()));
            if (ids.isEmpty()) {
                log.info("all objects were found in the cache, returning objects");
                return SearchResponse.<HouseholdMember>builder().response(objFound).build();
            }
        }

        String query = String.format("SELECT * FROM household_member where %s IN (:ids) AND isDeleted = false", columnName);
        if (null != includeDeleted && includeDeleted) {
            query = String.format("SELECT * FROM household_member WHERE %s IN (:ids)", columnName);
        }
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("ids", ids);

        objFound.addAll(this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper));
        fetchAndSetHouseholdMemberRelationship(objFound, includeDeleted);
        putInCache(objFound);
        log.info("returning objects from the database");
        return SearchResponse.<HouseholdMember>builder().response(objFound).build();
    }

    public SearchResponse<HouseholdMember> findIndividual(String individualId) {
        log.info("searching for HouseholdMember with individualId: {}", individualId);
        String query = "SELECT * FROM household_member where individualId = :individualId AND isDeleted = false";
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("individualId", individualId);
        List<HouseholdMember> householdMembers = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);
        fetchAndSetHouseholdMemberRelationship(householdMembers, false);
        return SearchResponse.<HouseholdMember>builder().totalCount((long) householdMembers.size()).response(householdMembers).build();
    }

    public SearchResponse<HouseholdMember> findIndividualByHousehold(String householdId, String columnName) {
        log.info("searching for HouseholdMembers with householdId: {}", householdId);
        String query = String.format("SELECT * FROM household_member where %s = :householdId AND isDeleted = false",
                columnName);
        Map<String, Object> paramMap = new HashMap();
        paramMap.put("householdId", householdId);

        Long totalCount = constructTotalCountCTEAndReturnResult(query, paramMap, this.namedParameterJdbcTemplate);

        List<HouseholdMember> householdMembers = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);

        return SearchResponse.<HouseholdMember>builder().totalCount(totalCount).response(householdMembers).build();
    }

    private void fetchAndSetHouseholdMemberRelationship(List<HouseholdMember> householdMembers, Boolean includeDeleted) {
        if (householdMembers.isEmpty()) {
            return;
        }
        List<String> householdMemberIds = getIdList(householdMembers);
        Map<String, Object> resourceParamsMap = new HashMap<>();
        StringBuilder resourceQuery = new StringBuilder("SELECT * FROM household_member_relationship hmr WHERE hmr.householdMemberId IN (:householdMemberIds)");
        resourceParamsMap.put("householdMemberIds", householdMemberIds);
        if (Boolean.FALSE.equals(includeDeleted)) {
            resourceQuery.append("AND isDeleted=:isDeleted ");
            resourceParamsMap.put("isDeleted", false);
        }
        List<Relationship> relationships = this.namedParameterJdbcTemplate.query(resourceQuery.toString(), resourceParamsMap,
                this.relationshipRowMapper);
        Map<String, List<Relationship>> idToObjMap = new HashMap<>();

        relationships.forEach(relationship -> {
            String memberId = relationship.getSelfId();
            if (idToObjMap.containsKey(memberId)) {
                idToObjMap.get(memberId).add(relationship);
            } else {
                List<Relationship> memberRelationships = new ArrayList<>();
                memberRelationships.add(relationship);
                idToObjMap.put(memberId, memberRelationships);
            }
        });
        householdMembers.forEach(member -> member.setRelationships(idToObjMap.get(member.getId())));
    }
}
