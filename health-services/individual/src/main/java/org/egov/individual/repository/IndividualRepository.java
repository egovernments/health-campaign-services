package org.egov.individual.repository;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.models.core.EgovModel;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.individual.Address;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.individual.Skill;
import org.egov.common.producer.Producer;
import org.egov.individual.repository.rowmapper.AddressRowMapper;
import org.egov.individual.repository.rowmapper.IdentifierRowMapper;
import org.egov.individual.repository.rowmapper.IndividualRowMapper;
import org.egov.individual.repository.rowmapper.SkillRowMapper;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.constructTotalCountCTEAndReturnResult;
import static org.egov.common.utils.CommonUtils.getIdMethod;

@Repository
@Slf4j
public class IndividualRepository extends GenericRepository<Individual> {

    private final String cteQuery = "WITH cte_search_criteria_waypoint(s_latitude, s_longitude) AS (VALUES(:s_latitude, :s_longitude))";
    private final String calculateDistanceFromTwoWaypointsFormulaQuery = "( 6371.4 * acos ( LEAST ( GREATEST (cos ( radians(cte_scw.s_latitude) ) * cos( radians(a.latitude) ) * cos( radians(a.longitude) - radians(cte_scw.s_longitude) )+ sin ( radians(cte_scw.s_latitude) ) * sin( radians(a.latitude) ), -1), 1) ) ) AS distance ";
    private final IdentifierRowMapper identifierRowMapper;
    private final AddressRowMapper addressRowMapper;
    private final SkillRowMapper skillRowMapper;

    protected IndividualRepository(@Qualifier("individualProducer")  Producer producer,
                                   NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                   RedisTemplate<String, Object> redisTemplate,
                                   SelectQueryBuilder selectQueryBuilder,
                                   IndividualRowMapper individualRowMapper, IdentifierRowMapper identifierRowMapper,
                                   AddressRowMapper addressRowMapper, SkillRowMapper skillRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate,
                selectQueryBuilder, individualRowMapper, Optional.of("individual"));
        this.identifierRowMapper = identifierRowMapper;
        this.addressRowMapper = addressRowMapper;

        this.skillRowMapper = skillRowMapper;
    }

    public SearchResponse<Individual> findById(List<String> ids, String idColumn, Boolean includeDeleted) {
        List<Individual> objFound = new ArrayList<>();
        try {
            objFound = findInCache(ids);
            if (!includeDeleted) {
                objFound = objFound.stream()
                        .filter(entity -> entity.getIsDeleted().equals(false))
                        .collect(Collectors.toList());
            }
            if (!objFound.isEmpty()) {
                Method idMethod = getIdMethod(objFound, idColumn);
                ids.removeAll(objFound.stream()
                        .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                        .collect(Collectors.toList()));
                if (ids.isEmpty()) {
                    return SearchResponse.<Individual>builder().totalCount(Long.valueOf(objFound.size())).response(objFound).build();
                }
            }
        }catch (Exception e){
            log.info("Error occurred while reading from cache", ExceptionUtils.getStackTrace(e));
        }

        String individualQuery = String.format(getQuery("SELECT * FROM individual WHERE %s IN (:ids)",
                includeDeleted), idColumn);
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);
        Long totalCount = constructTotalCountCTEAndReturnResult(individualQuery, paramMap, namedParameterJdbcTemplate);
        List<Individual> individuals = namedParameterJdbcTemplate
                .query(individualQuery, paramMap, rowMapper);
        enrichIndividuals(individuals, includeDeleted);
        objFound.addAll(individuals);
        putInCache(objFound);
        return SearchResponse.<Individual>builder().totalCount(totalCount).response(objFound).build();
    }

    public SearchResponse<Individual> find(IndividualSearch searchObject, Integer limit, Integer offset,
                                           String tenantId, Long lastChangedSince, Boolean includeDeleted) {
        Map<String, Object> paramsMap = new HashMap<>();
        String query = getQueryForIndividual(searchObject, limit, offset, tenantId, lastChangedSince,
                includeDeleted, paramsMap);
        if (isProximityBasedSearch(searchObject)) {
            return findByRadius(query, searchObject, includeDeleted, paramsMap);
        }
        if (searchObject.getIdentifier() == null) {
            String queryWithoutLimit = query.replace("ORDER BY id ASC LIMIT :limit OFFSET :offset", "");
            Long totalCount = constructTotalCountCTEAndReturnResult(queryWithoutLimit, paramsMap, namedParameterJdbcTemplate);
            List<Individual> individuals = namedParameterJdbcTemplate.query(query, paramsMap, rowMapper);
            if (!individuals.isEmpty()) {
                enrichIndividuals(individuals, includeDeleted);
            }
            return SearchResponse.<Individual>builder().totalCount(totalCount).response(individuals).build();
        } else {
            Map<String, Object> identifierParamMap = new HashMap<>();
            String identifierQuery = getIdentifierQuery(searchObject.getIdentifier(), identifierParamMap);
            identifierParamMap.put("isDeleted", includeDeleted);
            List<Identifier> identifiers = namedParameterJdbcTemplate
                    .query(identifierQuery, identifierParamMap, identifierRowMapper);
            if (!identifiers.isEmpty()) {
                query = query.replace(" tenantId=:tenantId ", " tenantId=:tenantId AND id=:individualId ");
                paramsMap.put("individualId", identifiers.stream().findAny().get().getIndividualId());
                List<Individual> individuals = namedParameterJdbcTemplate.query(query,
                        paramsMap, rowMapper);
                if (!individuals.isEmpty()) {
                    individuals.forEach(individual -> {
                        individual.setIdentifiers(identifiers);
                        List<Address> addresses = getAddressForIndividual(individual.getId(), includeDeleted);
                        individual.setAddress(addresses);
                        Map<String, Object> indServerGenIdParamMap = new HashMap<>();
                        indServerGenIdParamMap.put("individualId", individual.getId());
                        indServerGenIdParamMap.put("isDeleted", includeDeleted);
                        enrichSkills(includeDeleted, individual, indServerGenIdParamMap);
                    });
                }
                return SearchResponse.<Individual>builder().response(individuals).build();
            }
            return SearchResponse.<Individual>builder().build();
        }
    }

    /**
     * @param query
     * @param searchObject
     * @param includeDeleted
     * @param paramsMap
     * @return Fetch all the household which falls under the radius provided using longitude and latitude provided.
     */
    public SearchResponse<Individual> findByRadius(String query, IndividualSearch searchObject, Boolean includeDeleted, Map<String, Object> paramsMap) {
        query = query.replace("LIMIT :limit OFFSET :offset", "");
        paramsMap.put("s_latitude", searchObject.getLatitude());
        paramsMap.put("s_longitude", searchObject.getLongitude());
        if (searchObject.getIdentifier() != null) {
            Map<String, Object> identifierParamMap = new HashMap<>();
            String identifierQuery = getIdentifierQuery(searchObject.getIdentifier(), identifierParamMap);
            identifierParamMap.put("isDeleted", includeDeleted);
            List<Identifier> identifiers = namedParameterJdbcTemplate
                    .query(identifierQuery, identifierParamMap, identifierRowMapper);
            if (CollectionUtils.isEmpty(identifiers)) {
                query = query.replace(" tenantId=:tenantId ", " tenantId=:tenantId AND id=:individualId ");
                paramsMap.put("individualId", identifiers.stream().findAny().get().getIndividualId());
                query = cteQuery + ", cte_individual AS (" + query + ")";
                query = query + "SELECT * FROM (SELECT cte_i.*, " + calculateDistanceFromTwoWaypointsFormulaQuery
                        +" FROM cte_individual cte_i LEFT JOIN public.individual_address ia ON ia.individualid = cte_i.id LEFT JOIN public.address a ON ia.addressid = a.id , cte_search_criteria_waypoint cte_scw) rt ";
                if(searchObject.getSearchRadius() != null) {
                    query = query + " WHERE rt.distance < :distance ";
                }
                query = query + " ORDER BY distance ASC ";
                paramsMap.put("distance", searchObject.getSearchRadius());
                Long totalCount = constructTotalCountCTEAndReturnResult(query, paramsMap, namedParameterJdbcTemplate);
                query = query + "LIMIT :limit OFFSET :offset";
                List<Individual> individuals = namedParameterJdbcTemplate.query(query,
                        paramsMap, rowMapper);
                if (!individuals.isEmpty()) {
                    individuals.forEach(individual -> {
                        individual.setIdentifiers(identifiers);
                        List<Address> addresses = getAddressForIndividual(individual.getId(), includeDeleted);
                        individual.setAddress(addresses);
                        Map<String, Object> indServerGenIdParamMap = new HashMap<>();
                        indServerGenIdParamMap.put("individualId", individual.getId());
                        indServerGenIdParamMap.put("isDeleted", includeDeleted);
                        enrichSkills(includeDeleted, individual, indServerGenIdParamMap);
                    });
                }
                return SearchResponse.<Individual>builder().totalCount(totalCount).response(individuals).build();
            }
        } else {
            query = cteQuery + ", cte_individual AS (" + query + ")";
            query = query + "SELECT * FROM (SELECT cte_i.*, "+ calculateDistanceFromTwoWaypointsFormulaQuery
                    +" FROM cte_individual cte_i LEFT JOIN public.individual_address ia ON ia.individualid = cte_i.id LEFT JOIN public.address a ON ia.addressid = a.id , cte_search_criteria_waypoint cte_scw) rt ";
            if(searchObject.getSearchRadius() != null) {
                query = query + " WHERE rt.distance < :distance ";
            }
            query = query + " ORDER BY distance ASC ";
            paramsMap.put("distance", searchObject.getSearchRadius());

            Long totalCount = constructTotalCountCTEAndReturnResult(query, paramsMap, namedParameterJdbcTemplate);

            query = query + "LIMIT :limit OFFSET :offset";
            List<Individual> individuals = namedParameterJdbcTemplate.query(query,
                    paramsMap, rowMapper);
            if (!individuals.isEmpty()) {
                enrichIndividuals(individuals, includeDeleted);
            }
            return SearchResponse.<Individual>builder().totalCount(totalCount).response(individuals).build();
        }
        return SearchResponse.<Individual>builder().build();
    }


    private Boolean isProximityBasedSearch(IndividualSearch searchObject) {
        return searchObject.getLatitude() != null && searchObject.getLongitude() != null && searchObject.getSearchRadius() != null;
    }

    private void enrichSkills(Boolean includeDeleted, Individual individual, Map<String, Object> indServerGenIdParamMap) {
        String individualSkillQuery = getQuery("SELECT * FROM individual_skill WHERE individualId =:individualId",
                includeDeleted);
        List<Skill> skills = namedParameterJdbcTemplate.query(individualSkillQuery, indServerGenIdParamMap,
                skillRowMapper);
        individual.setSkills(skills);
    }

    private String getQueryForIndividual(IndividualSearch searchObject, Integer limit, Integer offset,
                                         String tenantId, Long lastChangedSince,
                                         Boolean includeDeleted, Map<String, Object> paramsMap) {
        String query = "SELECT * FROM individual";
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject, QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString().trim();

        query += " AND tenantId=:tenantId ";
        if (query.contains(tableName + " AND")) {
            query = query.replace(tableName + " AND", tableName + " WHERE ");
        }
        if (searchObject.getIndividualName() != null) {
            query = query + "AND givenname LIKE :individualName ";
            paramsMap.put("individualName", "%"+searchObject.getIndividualName()+"%");
        }
        if (searchObject.getGender() != null) {
            query = query + "AND gender =:gender ";
            paramsMap.put("gender", searchObject.getGender().name());
        }
        if (searchObject.getDateOfBirth() != null) {
            query = query + "AND dateOfBirth =:dateOfBirth ";
            paramsMap.put("dateOfBirth", searchObject.getDateOfBirth());
        }
        if (searchObject.getSocialCategory() != null) {
            query = query + "AND additionaldetails->'fields' @> '[{\"key\": \"SOCIAL_CATEGORY\", \"value\":" + "\"" + searchObject.getSocialCategory() + "\"}]' ";
        }
        if (searchObject.getCreatedFrom() != null) {

            //If user does not specify toDate, take today's date as toDate by default.
            if (searchObject.getCreatedTo() == null) {
                searchObject.setCreatedTo(new BigDecimal(Instant.now().toEpochMilli()));
            }
            query = query + "AND createdTime BETWEEN :createdFrom AND :createdTo ";
            paramsMap.put("createdFrom", searchObject.getCreatedFrom());
            paramsMap.put("createdTo", searchObject.getCreatedTo());

        } else {
            //if only toDate is provided as parameter without fromDate parameter, throw an exception.
            if (searchObject.getCreatedTo() != null) {
                throw new CustomException("INVALID_SEARCH_PARAM", "Cannot specify createdToDate without a createdFromDate");
            }
        }
        if (Boolean.FALSE.equals(includeDeleted)) {
            query = query + "AND isDeleted=:isDeleted ";
        }

        if (lastChangedSince != null) {
            query = query + "AND lastModifiedTime>=:lastModifiedTime ";
        }
        if (searchObject.getRoleCodes() != null && !searchObject.getRoleCodes().isEmpty()) {
            query = query + "AND (";
            for (int i = 0; i < searchObject.getRoleCodes().size(); i++) {
                query = query + "roles @> '[{\"code\": \"" + searchObject.getRoleCodes().get(i) + "\"}]'";
                if (i != searchObject.getRoleCodes().size() - 1) {
                    query = query + " OR ";  // Add OR between conditions
                }
            }
            query = query + ") ";
        }

        if (searchObject.getUsername() != null) {
            query = query + "AND username in (:username) ";
            paramsMap.put("username", searchObject.getUsername());
        }

        if (searchObject.getUserId() != null) {
            query = query + "AND userId in (:userId) ";
            paramsMap.put("userId", searchObject.getUserId().stream()
                    .map(Object::toString)
                    .collect(Collectors.toList()));
        }
      
        if (searchObject.getUserUuid() != null) {
            query = query + "AND userUuid in (:userUuid) ";
            paramsMap.put("userUuid", searchObject.getUserUuid());
        }

        query = query + "ORDER BY createdtime DESC LIMIT :limit OFFSET :offset";
      
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);

        log.info("query-------------------------->");
        log.info(query);
        return query;
    }

    private String getIdentifierQuery(Identifier identifier, Map<String, Object> paramMap) {
        String identifierQuery = "SELECT * FROM individual_identifier";
        List<String> identifierWhereFields = GenericQueryBuilder.getFieldsWithCondition(identifier,
                QueryFieldChecker.isNotNull, paramMap);
        return GenericQueryBuilder.generateQuery(identifierQuery, identifierWhereFields).toString();
    }

    private List<Address> getAddressForIndividual(String individualId, Boolean includeDeleted) {
        String addressQuery = getQuery("SELECT a.*, ia.individualId, ia.addressId, ia.createdBy, ia.lastModifiedBy, ia.createdTime, ia.lastModifiedTime, ia.isDeleted" +
                " FROM (" +
                "    SELECT individualId, addressId, type, createdBy, lastModifiedBy, createdTime, lastModifiedTime, isDeleted, " +
                "           ROW_NUMBER() OVER (PARTITION BY individualId, type ORDER BY lastModifiedTime DESC) AS rn" +
                "    FROM individual_address" +
                "    WHERE individualId = :individualId" +
                " ) AS ia" +
                " JOIN address AS a ON ia.addressId = a.id" +
                " WHERE ia.rn = 1 ", includeDeleted, "ia");
        Map<String, Object> indServerGenIdParamMap = new HashMap<>();
        indServerGenIdParamMap.put("individualId", individualId);
        indServerGenIdParamMap.put("isDeleted", includeDeleted);
        return namedParameterJdbcTemplate
                .query(addressQuery, indServerGenIdParamMap, addressRowMapper);
    }

    private void enrichIndividuals(List<Individual> individuals, Boolean includeDeleted) {
        if (!individuals.isEmpty()) {
            Map<String, Object> paramsMap = new HashMap<>();
            paramsMap.put("individuals", individuals.parallelStream()
                    .map(EgovModel::getId).collect(Collectors.toList()));
            paramsMap.put("isDeleted", includeDeleted);

            enrichIdentifiers(individuals, paramsMap, includeDeleted);
            enrichSkills(individuals, paramsMap, includeDeleted);
            enrichIndividualWithAddress(individuals, paramsMap, includeDeleted);
        }
    }


    private void enrichIndividualWithAddress(List<Individual> individuals, Map<String, Object> paramsMap, Boolean includeDeleted) {
        String addressQuery = getQuery("SELECT a.*, ia.individualId, ia.addressId, ia.createdBy, ia.lastModifiedBy, ia.createdTime, ia.lastModifiedTime, ia.isDeleted" +
                " FROM (" +
                "    SELECT individualId, addressId, type, createdBy, lastModifiedBy, createdTime, lastModifiedTime, isDeleted, " +
                "           ROW_NUMBER() OVER (PARTITION BY individualId, type ORDER BY lastModifiedTime DESC) AS rn" +
                "    FROM individual_address" +
                "    WHERE individualId IN (:individuals)" +
                " ) AS ia" +
                " JOIN address AS a ON ia.addressId = a.id" +
                " WHERE ia.rn = 1 ", includeDeleted, "ia");
        List<Address> addressList = namedParameterJdbcTemplate.query(addressQuery, paramsMap, addressRowMapper);

        Map<String, List<Address>> addressMap = addressList.parallelStream()
                .collect(Collectors.groupingByConcurrent(Address::getIndividualId));
        individuals.parallelStream().forEach(
                individual -> individual.setAddress(addressMap.get(individual.getId())));
    }

    private void enrichIdentifiers(List<Individual> individuals, Map<String, Object> paramsMap,
                                   Boolean includeDeleted) {
        String individualIdentifierQuery = getQuery(
                "SELECT * FROM individual_identifier ii WHERE ii.individualId IN (:individuals)",
                includeDeleted);
        List<Identifier> identifiers = namedParameterJdbcTemplate
                .query(individualIdentifierQuery, paramsMap, identifierRowMapper);

        Map<String, List<Identifier>> identifierMap = identifiers.parallelStream()
                .collect(Collectors.groupingByConcurrent(Identifier::getIndividualId));
        individuals.parallelStream().forEach(
                individual -> individual.setIdentifiers(identifierMap.get(individual.getId())));
    }

    private void enrichSkills(List<Individual> individuals, Map<String, Object> paramsMap,
                              Boolean includeDeleted) {
        String individualSkillQuery = getQuery(
                "SELECT * FROM individual_skill WHERE individualId IN (:individuals)",
                includeDeleted);
        List<Skill> skills = namedParameterJdbcTemplate.query(individualSkillQuery, paramsMap,
                skillRowMapper);

        Map<String, List<Skill>> skillsMap = skills.parallelStream()
                .collect(Collectors.groupingByConcurrent(Skill::getIndividualId));
        individuals.parallelStream().forEach(
                individual -> individual.setSkills(skillsMap.get(individual.getId())));
    }

    private String getQuery(String baseQuery, Boolean includeDeleted) {
        return getQuery(baseQuery, includeDeleted, null);
    }

    private String getQuery(String baseQuery, Boolean includeDeleted, String alias) {
        String isDeletedClause = " AND %sisDeleted = false";
        if (alias != null) {
            isDeletedClause = String.format(isDeletedClause, alias + ".");
        } else {
            isDeletedClause = String.format(isDeletedClause, "");
        }
        StringBuilder baseQueryBuilder = new StringBuilder(baseQuery);
        if (null != includeDeleted && includeDeleted) {
            return baseQuery;
        } else {
            baseQueryBuilder.append(isDeletedClause);
        }
        return baseQueryBuilder.toString();
    }
}
