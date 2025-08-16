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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.individual.Address;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.individual.Skill;
import org.egov.common.producer.Producer;
import org.egov.common.utils.CommonUtils;
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
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.constructTotalCountCTEAndReturnResult;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;
import static org.egov.individual.Constants.INVALID_TENANT_ID;
import static org.egov.individual.Constants.INVALID_TENANT_ID_MSG;

@Repository
@Slf4j
public class IndividualRepository extends GenericRepository<Individual> {

    private final String cteQuery = "WITH cte_search_criteria_waypoint(s_latitude, s_longitude) AS (VALUES(:s_latitude, :s_longitude))";
    private final String calculateDistanceFromTwoWaypointsFormulaQuery = "( 6371.4 * acos ( LEAST ( GREATEST (cos ( radians(cte_scw.s_latitude) ) * cos( radians(a.latitude) ) * cos( radians(a.longitude) - radians(cte_scw.s_longitude) )+ sin ( radians(cte_scw.s_latitude) ) * sin( radians(a.latitude) ), -1), 1) ) ) AS distance ";

    protected IndividualRepository(@Qualifier("individualProducer")  Producer producer,
                                   NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                   RedisTemplate<String, Object> redisTemplate,
                                   SelectQueryBuilder selectQueryBuilder,
                                   IndividualRowMapper individualRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate,
                selectQueryBuilder, individualRowMapper, Optional.of("individual"));
    }

    /**
     * This method fetches the list of individuals based on the provided IDs.
     *
     * @param tenantId       The tenant ID for which the search is being performed.
     * @param ids            The list of IDs to search for.
     * @param idColumn       The column name representing the ID in the database.
     * @param includeDeleted  Flag indicating whether to include deleted records.
     * @return SearchResponse<Individual> A response object containing the total count and the list of individuals found.
     */
    public SearchResponse<Individual> findById(String tenantId, List<String> ids, String idColumn, Boolean includeDeleted) throws InvalidTenantIdException {
        List<Individual> objFound = new ArrayList<>();
        // Check if the list of IDs is empty
        try {
            objFound = findInCache( tenantId, ids);
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

        // If the list of IDs is not empty, proceed to fetch from the database
        // add the schema placeholder to the query
        String individualQuery = String.format(getQuery("SELECT * FROM %s.individual WHERE %s IN (:ids)",
                includeDeleted), SCHEMA_REPLACE_STRING , idColumn);
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);

        // replace the schema placeholder with the tenantId
        individualQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(individualQuery, tenantId);
        Long totalCount = constructTotalCountCTEAndReturnResult(individualQuery, paramMap, this.namedParameterJdbcTemplate);
        List<Individual> individuals = this.namedParameterJdbcTemplate
                .query(individualQuery, paramMap, this.rowMapper);
        enrichIndividuals(individuals, includeDeleted);
        objFound.addAll(individuals);
        putInCache(objFound);
        return SearchResponse.<Individual>builder().totalCount(totalCount).response(objFound).build();
    }

    /**
     * This method fetches the list of individuals based on the search criteria provided.
     *
     * @param searchObject    The criteria used to filter the individuals.
     * @param limit           The maximum number of records to return.
     * @param offset          The offset for pagination.
     * @param tenantId        The tenant ID for which the search is being performed.
     * @param lastChangedSince Timestamp indicating when the records were last changed.
     * @param includeDeleted   Flag indicating whether to include deleted records.
     * @return SearchResponse<Individual> A response object containing the total count and the list of individuals found.
     */
    public SearchResponse<Individual> find(IndividualSearch searchObject, Integer limit, Integer offset,
                                           String tenantId, Long lastChangedSince, Boolean includeDeleted) throws InvalidTenantIdException {
        Map<String, Object> paramsMap = new HashMap<>();
        String query = getQueryForIndividual(searchObject, limit, offset, tenantId, lastChangedSince,
                includeDeleted, paramsMap);
        if (isProximityBasedSearch(searchObject)) {
            // If latitude, longitude and search radius are provided, call the findByRadius method
            return findByRadius(tenantId, query, searchObject, includeDeleted, paramsMap);
        }
        if (searchObject.getIdentifier() == null) {
            String queryWithoutLimit = query.replace("ORDER BY createdtime DESC LIMIT :limit OFFSET :offset", "");
            Long totalCount = constructTotalCountCTEAndReturnResult(queryWithoutLimit, paramsMap, this.namedParameterJdbcTemplate);
            List<Individual> individuals = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);
            if (!individuals.isEmpty()) {
                enrichIndividuals(individuals, includeDeleted);
            }
            return SearchResponse.<Individual>builder().totalCount(totalCount).response(individuals).build();
        } else {
            Map<String, Object> identifierParamMap = new HashMap<>();
            // If identifier is provided, fetch the identifiers first
            String identifierQuery = getIdentifierQuery(tenantId, searchObject.getIdentifier(), identifierParamMap);
            identifierParamMap.put("isDeleted", includeDeleted);
            List<Identifier> identifiers = this.namedParameterJdbcTemplate
                    .query(identifierQuery, identifierParamMap, new IdentifierRowMapper());
            if (!identifiers.isEmpty()) {
                String individualId = identifiers.stream().findAny().get().getIndividualId();
                String individualClientRefId = identifiers.stream().findAny().get().getIndividualClientReferenceId();
                if (!ObjectUtils.isEmpty(individualId)) {
                    // If individualId is present, use it to filter the query
                    query = query.replace(" tenantId=:tenantId ", " tenantId=:tenantId AND id=:individualId ");
                    paramsMap.put("individualId", individualId);
                } else {
                    // If individualClientReferenceId is present, use it to filter the query
                    query = query.replace(" tenantId=:tenantId ", " tenantId=:tenantId AND clientReferenceId=:individualClientReferenceId ");
                    paramsMap.put("individualClientReferenceId", individualClientRefId);
                }
                List<Individual> individuals = this.namedParameterJdbcTemplate.query(query,
                        paramsMap, this.rowMapper);
                if (!individuals.isEmpty()) {
                    individuals.forEach(individual -> {
                        individual.setIdentifiers(identifiers);
                        List<Address> addresses = null;
                        // Fetch the addresses for each individual
                        // catch the InvalidTenantIdException and throw a custom exception
                        try {
                            addresses = getAddressForIndividual( tenantId, individual.getId(), includeDeleted);
                        } catch (InvalidTenantIdException e) {
                            throw new CustomException( INVALID_TENANT_ID , INVALID_TENANT_ID_MSG);
                        }
                        individual.setAddress(addresses);
                        Map<String, Object> indServerGenIdParamMap = new HashMap<>();
                        indServerGenIdParamMap.put("individualId", individual.getId());
                        indServerGenIdParamMap.put("isDeleted", includeDeleted);
                        // catch the InvalidTenantIdException and throw a custom exception
                        try {
                            enrichSkills(includeDeleted, individual, indServerGenIdParamMap);
                        } catch (InvalidTenantIdException e) {
                            throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
                        }
                    });
                }
                return SearchResponse.<Individual>builder().response(individuals).build();
            }
            return SearchResponse.<Individual>builder().build();
        }
    }
    public SearchResponse<Individual> findByName(String givenName, String familyName, String otherNames, String tenantId, Integer limit, Integer offset, Boolean includeDeleted) {
        Map<String, Object> paramsMap = new HashMap<>();
        String query = "SELECT * FROM individual WHERE tenantId = :tenantId";

        if (StringUtils.isNotBlank(givenName)) {
            query += " AND givenName ILIKE :givenName";
            paramsMap.put("givenName", "%" + givenName + "%");
        }

        if (StringUtils.isNotBlank(familyName)) {
            query += " AND familyName ILIKE :familyName";
            paramsMap.put("familyName", "%" + familyName + "%");
        }

        if (StringUtils.isNotBlank(otherNames)) {
            query += " AND otherNames ILIKE :otherNames";
            paramsMap.put("otherNames", "%" + otherNames + "%");
        }

        if (Boolean.FALSE.equals(includeDeleted)) {
            query += " AND isDeleted = false";
        }

        query += " ORDER BY createdTime DESC LIMIT :limit OFFSET :offset";

        paramsMap.put("tenantId", tenantId);
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);

        List<Individual> individuals = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);

        return SearchResponse.<Individual>builder()
                .totalCount((long) individuals.size())
                .response(individuals)
                .build();
    }
    /**
     * @param query
     * @param searchObject
     * @param includeDeleted
     * @param paramsMap
     * @return Fetch all the household which falls under the radius provided using longitude and latitude provided.
     */
    public SearchResponse<Individual> findByRadius(String tenantId, String query, IndividualSearch searchObject, Boolean includeDeleted, Map<String, Object> paramsMap) throws InvalidTenantIdException {
        query = query.replace("LIMIT :limit OFFSET :offset", "");
        paramsMap.put("s_latitude", searchObject.getLatitude());
        paramsMap.put("s_longitude", searchObject.getLongitude());
        if (searchObject.getIdentifier() != null) {
            Map<String, Object> identifierParamMap = new HashMap<>();
            String identifierQuery = getIdentifierQuery(tenantId, searchObject.getIdentifier(), identifierParamMap);
            identifierParamMap.put("isDeleted", includeDeleted);
            List<Identifier> identifiers = this.namedParameterJdbcTemplate
                    .query(identifierQuery, identifierParamMap, new IdentifierRowMapper());
            if (CollectionUtils.isEmpty(identifiers)) {
                query = query.replace(" tenantId=:tenantId ", " tenantId=:tenantId AND id=:individualId ");
                paramsMap.put("individualId", identifiers.stream().findAny().get().getIndividualId());
                query = cteQuery + ", cte_individual AS (" + query + ")";
                query = query + "SELECT * FROM (SELECT cte_i.*, " + calculateDistanceFromTwoWaypointsFormulaQuery
                        + String.format(" FROM cte_individual cte_i LEFT JOIN %s.individual_address ia ON ia.individualid = cte_i.id LEFT JOIN %s.address a ON ia.addressid = a.id , cte_search_criteria_waypoint cte_scw) rt ", SCHEMA_REPLACE_STRING, SCHEMA_REPLACE_STRING);
                if(searchObject.getSearchRadius() != null) {
                    query = query + " WHERE rt.distance < :distance ";
                }
                query = query + " ORDER BY distance ASC ";
                try {
                    query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
                } catch (InvalidTenantIdException e) {
                    throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
                }
                paramsMap.put("distance", searchObject.getSearchRadius());
                Long totalCount = constructTotalCountCTEAndReturnResult(query, paramsMap, this.namedParameterJdbcTemplate);
                query = query + "LIMIT :limit OFFSET :offset";
                List<Individual> individuals = this.namedParameterJdbcTemplate.query(query,
                        paramsMap, this.rowMapper);
                if (!individuals.isEmpty()) {
                    individuals.forEach(individual -> {
                        individual.setIdentifiers(identifiers);
                        List<Address> addresses = null;
                        try {
                            addresses = getAddressForIndividual(tenantId, individual.getId(), includeDeleted);
                        } catch (InvalidTenantIdException e) {
                            throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
                        }
                        individual.setAddress(addresses);
                        Map<String, Object> indServerGenIdParamMap = new HashMap<>();
                        indServerGenIdParamMap.put("individualId", individual.getId());
                        indServerGenIdParamMap.put("isDeleted", includeDeleted);
                        try {
                            enrichSkills(includeDeleted, individual, indServerGenIdParamMap);
                        } catch (InvalidTenantIdException e) {
                            throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
                        }
                    });
                }
                return SearchResponse.<Individual>builder().totalCount(totalCount).response(individuals).build();
            }
        } else {
            query = cteQuery + ", cte_individual AS (" + query + ")";
            query = query + "SELECT * FROM (SELECT cte_i.*, "+ calculateDistanceFromTwoWaypointsFormulaQuery
                    +" FROM cte_individual cte_i LEFT JOIN %s.individual_address ia ON ia.individualid = cte_i.id LEFT JOIN %s.address a ON ia.addressid = a.id , cte_search_criteria_waypoint cte_scw) rt ";
            query = String.format(query, SCHEMA_REPLACE_STRING, SCHEMA_REPLACE_STRING);
            if(searchObject.getSearchRadius() != null) {
                query = query + " WHERE rt.distance < :distance ";
            }
            query = query + " ORDER BY distance ASC ";
            paramsMap.put("distance", searchObject.getSearchRadius());
            query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
            Long totalCount = constructTotalCountCTEAndReturnResult(query, paramsMap, this.namedParameterJdbcTemplate);

            query = query + "LIMIT :limit OFFSET :offset";
            List<Individual> individuals = this.namedParameterJdbcTemplate.query(query,
                    paramsMap, this.rowMapper);
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

    private void enrichSkills(Boolean includeDeleted, Individual individual, Map<String, Object> indServerGenIdParamMap) throws InvalidTenantIdException {
        String individualSkillQuery = getQuery("SELECT * FROM %s.individual_skill WHERE individualId =:individualId",
                includeDeleted);
        individualSkillQuery = String.format(individualSkillQuery, SCHEMA_REPLACE_STRING);
        individualSkillQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(individualSkillQuery, individual.getTenantId());
        List<Skill> skills = this.namedParameterJdbcTemplate.query(individualSkillQuery, indServerGenIdParamMap,
                new SkillRowMapper());
        individual.setSkills(skills);
    }

    private String getQueryForIndividual(IndividualSearch searchObject, Integer limit, Integer offset,
                                         String tenantId, Long lastChangedSince,
                                         Boolean includeDeleted, Map<String, Object> paramsMap) throws InvalidTenantIdException {

        String query = String.format("SELECT * FROM %s.individual", SCHEMA_REPLACE_STRING);
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject, QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString().trim();

        query += " AND tenantId=:tenantId ";
        if (query.contains(tableName + " AND")) {
            query = query.replace(tableName + " AND", tableName + " WHERE ");
        }
        if (searchObject.getIndividualName() != null) {
            query = query + "AND givenname ILIKE :individualName ";
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

        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        log.info("query-------------------------->");
        log.info(query);
        return query;
    }

    private String getIdentifierQuery(String tenantId, Identifier identifier, Map<String, Object> paramMap) throws InvalidTenantIdException {
        String identifierQuery = String.format("SELECT * FROM %s.individual_identifier", SCHEMA_REPLACE_STRING);

        identifierQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(identifierQuery, tenantId);
        List<String> identifierWhereFields = GenericQueryBuilder.getFieldsWithCondition(identifier,
                QueryFieldChecker.isNotNull, paramMap);
        return GenericQueryBuilder.generateQuery(identifierQuery, identifierWhereFields).toString();
    }

    private List<Address> getAddressForIndividual(String tenantId, String individualId, Boolean includeDeleted) throws InvalidTenantIdException {
        String addressQuery = getQuery("SELECT a.*, ia.individualId, ia.addressId, ia.createdBy, ia.lastModifiedBy, ia.createdTime, ia.lastModifiedTime, ia.isDeleted" +
                " FROM (" +
                "    SELECT individualId, addressId, type, createdBy, lastModifiedBy, createdTime, lastModifiedTime, isDeleted, " +
                "           ROW_NUMBER() OVER (PARTITION BY individualId, type ORDER BY lastModifiedTime DESC) AS rn" +
                "    FROM %s.individual_address" +
                "    WHERE individualId = :individualId" +
                " ) AS ia" +
                " JOIN %s.address AS a ON ia.addressId = a.id" +
                " WHERE ia.rn = 1 ", includeDeleted, "ia");
        addressQuery = String.format(addressQuery,SCHEMA_REPLACE_STRING, SCHEMA_REPLACE_STRING );
        Map<String, Object> indServerGenIdParamMap = new HashMap<>();
        indServerGenIdParamMap.put("individualId", individualId);
        indServerGenIdParamMap.put("isDeleted", includeDeleted);
        addressQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(addressQuery, tenantId);
        return this.namedParameterJdbcTemplate
                .query(addressQuery, indServerGenIdParamMap, new AddressRowMapper());
    }

    private void enrichIndividuals(List<Individual> individuals, Boolean includeDeleted) {
        if (!individuals.isEmpty()) {
            String tenantId = CommonUtils.getTenantId(individuals);
            individuals.forEach(individual -> {
                Map<String, Object> indServerGenIdParamMap = new HashMap<>();
                indServerGenIdParamMap.put("individualId", individual.getId());
                indServerGenIdParamMap.put("clientReferenceId", individual.getClientReferenceId());
                indServerGenIdParamMap.put("isDeleted", includeDeleted);
                List<Address> addresses = null;
                try {
                    addresses = getAddressForIndividual( tenantId, individual.getId(), includeDeleted);
                } catch (InvalidTenantIdException e) {
                    throw new RuntimeException(e);
                }
                // Constructing the base query for identifiers
                String baseQuery = "SELECT * FROM %s.individual_identifier ii WHERE ii.individualId =:individualId ";
                if (!ObjectUtils.isEmpty(individual.getId()) && !ObjectUtils.isEmpty(individual.getClientReferenceId())) {
                    // If both individualId and clientReferenceId are present, use them in the query
                    baseQuery = "SELECT * FROM %s.individual_identifier ii WHERE (ii.individualId =:individualId OR ii.individualClientReferenceId=:clientReferenceId) ";
                }
                String individualIdentifierQuery = String.format(getQuery(baseQuery, includeDeleted), SCHEMA_REPLACE_STRING);
                try {
                    individualIdentifierQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(individualIdentifierQuery, tenantId);
                } catch (InvalidTenantIdException e) {
                    throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
                }
                List<Identifier> identifiers = this.namedParameterJdbcTemplate
                        .query(individualIdentifierQuery, indServerGenIdParamMap,
                                new IdentifierRowMapper());
                try {
                    enrichSkills(includeDeleted, individual, indServerGenIdParamMap);
                } catch (InvalidTenantIdException e) {
                    throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
                }
                individual.setAddress(addresses);
                individual.setIdentifiers(identifiers);
            });
        }
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