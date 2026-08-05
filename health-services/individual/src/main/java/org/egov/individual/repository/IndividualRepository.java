package org.egov.individual.repository;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.models.AuditDetails;
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
                    // identifiers come from the search criterion and are deliberately narrower than
                    // all identifiers of the individual, so they are not re-fetched here
                    individuals.forEach(individual -> individual.setIdentifiers(new ArrayList<>(identifiers)));
                    try {
                        enrichAddressAndSkills(tenantId, individuals, includeDeleted);
                    } catch (InvalidTenantIdException e) {
                        throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
                    }
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
            if (!CollectionUtils.isEmpty(identifiers)) {
                String individualId = identifiers.stream().findAny().get().getIndividualId();
                String individualClientRefId = identifiers.stream().findAny().get().getIndividualClientReferenceId();
                if (!ObjectUtils.isEmpty(individualId)) {
                    query = query.replace(" tenantId=:tenantId ", " tenantId=:tenantId AND id=:individualId ");
                    paramsMap.put("individualId", individualId);
                } else {
                    query = query.replace(" tenantId=:tenantId ", " tenantId=:tenantId AND clientReferenceId=:individualClientReferenceId ");
                    paramsMap.put("individualClientReferenceId", individualClientRefId);
                }
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
                    // identifiers come from the search criterion and are deliberately narrower than
                    // all identifiers of the individual, so they are not re-fetched here
                    individuals.forEach(individual -> individual.setIdentifiers(new ArrayList<>(identifiers)));
                    try {
                        enrichAddressAndSkills(tenantId, individuals, includeDeleted);
                    } catch (InvalidTenantIdException e) {
                        throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
                    }
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

    private Map<String, List<Skill>> fetchSkills(String tenantId, List<String> individualIds,
                                                 Boolean includeDeleted) throws InvalidTenantIdException {
        if (CollectionUtils.isEmpty(individualIds)) {
            return Collections.emptyMap();
        }
        String individualSkillQuery = getQuery("SELECT * FROM %s.individual_skill WHERE individualId IN (:individualIds)",
                includeDeleted);
        individualSkillQuery = String.format(individualSkillQuery, SCHEMA_REPLACE_STRING);
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("individualIds", individualIds);
        individualSkillQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(individualSkillQuery, tenantId);
        List<Skill> skills = this.namedParameterJdbcTemplate.query(individualSkillQuery, paramMap,
                new SkillRowMapper());
        return skills.stream()
                .filter(skill -> skill.getIndividualId() != null)
                .collect(Collectors.groupingBy(Skill::getIndividualId));
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


        // ---- NEW: DB-level boundary/ward filter using ADDRESS.localityCode / ADDRESS.wardCode ----
        if (searchObject.getBoundaryCode() != null || searchObject.getWardCode() != null) {
            StringBuilder addrExists = new StringBuilder();
            addrExists.append(" AND EXISTS ( ");
            addrExists.append("   SELECT 1 ");
            addrExists.append("   FROM ").append(SCHEMA_REPLACE_STRING).append(".individual_address ia ");
            addrExists.append("   JOIN ").append(SCHEMA_REPLACE_STRING).append(".address a ON a.id = ia.addressId ");
            addrExists.append("   WHERE ia.individualId = individual.id ");

            // Only constrain IA deletion when includeDeleted == false
            if (Boolean.FALSE.equals(includeDeleted)) {
                addrExists.append("     AND ia.isDeleted = false ");
            }

            if (searchObject.getBoundaryCode() != null) {
                addrExists.append("     AND a.localityCode = :boundaryCode ");
                paramsMap.put("boundaryCode", searchObject.getBoundaryCode());
            }
            if (searchObject.getWardCode() != null) {
                addrExists.append("     AND a.wardCode = :wardCode ");
                paramsMap.put("wardCode", searchObject.getWardCode());
            }
            addrExists.append(" ) ");

            query = query + addrExists.toString();
        }
        // --
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

    private Map<String, List<Address>> fetchAddresses(String tenantId, List<String> individualIds,
                                                      Boolean includeDeleted) throws InvalidTenantIdException {
        if (CollectionUtils.isEmpty(individualIds)) {
            return Collections.emptyMap();
        }
        // individualId is the leading partition key, so widening the predicate to an IN list cannot
        // change any row's rn: ranking restarts per individual. Latest-per-(individual, type) is preserved.
        String addressQuery = getQuery("SELECT a.*, ia.individualId, ia.addressId, ia.createdBy, ia.lastModifiedBy, ia.createdTime, ia.lastModifiedTime, ia.isDeleted" +
                " FROM (" +
                "    SELECT individualId, addressId, type, createdBy, lastModifiedBy, createdTime, lastModifiedTime, isDeleted, " +
                "           ROW_NUMBER() OVER (PARTITION BY individualId, type ORDER BY lastModifiedTime DESC) AS rn" +
                "    FROM %s.individual_address" +
                "    WHERE individualId IN (:individualIds)" +
                " ) AS ia" +
                " JOIN %s.address AS a ON ia.addressId = a.id" +
                " WHERE ia.rn = 1 ", includeDeleted, "ia");
        addressQuery = String.format(addressQuery,SCHEMA_REPLACE_STRING, SCHEMA_REPLACE_STRING );
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("individualIds", individualIds);
        addressQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(addressQuery, tenantId);
        List<Address> addresses = this.namedParameterJdbcTemplate
                .query(addressQuery, paramMap, new AddressRowMapper());
        return addresses.stream()
                .filter(address -> address.getIndividualId() != null)
                .collect(Collectors.groupingBy(Address::getIndividualId));
    }

    private void enrichIndividuals(List<Individual> individuals, Boolean includeDeleted) {
        if (CollectionUtils.isEmpty(individuals)) {
            return;
        }
        String tenantId = CommonUtils.getTenantId(individuals);
        try {
            IdentifierIndex identifierIndex = fetchIdentifiers(tenantId, collectIndividualIds(individuals),
                    collectClientReferenceIds(individuals), includeDeleted);
            enrichAddressAndSkills(tenantId, individuals, includeDeleted);
            individuals.forEach(individual -> individual.setIdentifiers(resolveIdentifiers(individual, identifierIndex)));
        } catch (InvalidTenantIdException e) {
            // preserves the pre-batching error surface: the first per-row failure was wrapped this way
            throw new RuntimeException(e);
        }
    }

    /**
     * Batched address and skill enrichment. Used by the identifier-search branches too, where the
     * identifiers are already known from the search query and must not be re-fetched.
     */
    private void enrichAddressAndSkills(String tenantId, List<Individual> individuals,
                                        Boolean includeDeleted) throws InvalidTenantIdException {
        if (CollectionUtils.isEmpty(individuals)) {
            return;
        }
        List<String> individualIds = collectIndividualIds(individuals);
        Map<String, List<Address>> addressMap = fetchAddresses(tenantId, individualIds, includeDeleted);
        Map<String, List<Skill>> skillMap = fetchSkills(tenantId, individualIds, includeDeleted);
        individuals.forEach(individual -> {
            individual.setAddress(copyOf(addressMap.get(individual.getId())));
            individual.setSkills(copyOf(skillMap.get(individual.getId())));
        });
    }

    private IdentifierIndex fetchIdentifiers(String tenantId, List<String> individualIds,
                                             List<String> clientReferenceIds,
                                             Boolean includeDeleted) throws InvalidTenantIdException {
        if (CollectionUtils.isEmpty(individualIds) && CollectionUtils.isEmpty(clientReferenceIds)) {
            return new IdentifierIndex(Collections.emptyMap(), Collections.emptyMap());
        }
        Map<String, Object> paramMap = new HashMap<>();
        StringBuilder predicate = new StringBuilder();
        if (!CollectionUtils.isEmpty(individualIds)) {
            predicate.append("ii.individualId IN (:individualIds)");
            paramMap.put("individualIds", individualIds);
        }
        // appended only when non-empty: an empty collection expands to IN (), which Postgres rejects
        if (!CollectionUtils.isEmpty(clientReferenceIds)) {
            if (predicate.length() > 0) {
                predicate.append(" OR ");
            }
            predicate.append("ii.individualClientReferenceId IN (:clientReferenceIds)");
            paramMap.put("clientReferenceIds", clientReferenceIds);
        }
        String baseQuery = "SELECT * FROM %s.individual_identifier ii WHERE (" + predicate + ") ";
        String individualIdentifierQuery = String.format(getQuery(baseQuery, includeDeleted), SCHEMA_REPLACE_STRING);
        individualIdentifierQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(individualIdentifierQuery, tenantId);
        List<Identifier> identifiers = this.namedParameterJdbcTemplate
                .query(individualIdentifierQuery, paramMap, new IdentifierRowMapper());
        return new IdentifierIndex(
                identifiers.stream()
                        .filter(identifier -> identifier.getIndividualId() != null)
                        .collect(Collectors.groupingBy(Identifier::getIndividualId)),
                identifiers.stream()
                        .filter(identifier -> identifier.getIndividualClientReferenceId() != null)
                        .collect(Collectors.groupingBy(Identifier::getIndividualClientReferenceId)));
    }

    /**
     * Re-applies the original per-individual identifier predicate against the batched result, so the
     * deliberately broader IN-list fetch yields exactly what the per-row query returned.
     */
    private List<Identifier> resolveIdentifiers(Individual individual, IdentifierIndex index) {
        if (!ObjectUtils.isEmpty(individual.getId()) && !ObjectUtils.isEmpty(individual.getClientReferenceId())) {
            // a row matching both arms must appear once, as SQL OR would return it once
            Map<String, Identifier> deduped = new LinkedHashMap<>();
            index.byIndividualId.getOrDefault(individual.getId(), Collections.emptyList())
                    .forEach(identifier -> deduped.put(identifier.getId(), identifier));
            index.byIndividualClientReferenceId
                    .getOrDefault(individual.getClientReferenceId(), Collections.emptyList())
                    .forEach(identifier -> deduped.put(identifier.getId(), identifier));
            return deduped.values().stream().map(this::copyOfIdentifier)
                    .collect(Collectors.toList());
        }
        // matches only on individualId, which for a null id is "= NULL" and therefore no rows
        return index.byIndividualId.getOrDefault(individual.getId(), Collections.emptyList()).stream()
                .map(this::copyOfIdentifier)
                .collect(Collectors.toList());
    }

    /**
     * The per-row queries mapped a fresh Identifier per individual; a row resolved for two
     * individuals (shared client reference id) must not become a shared instance.
     */
    private Identifier copyOfIdentifier(Identifier source) {
        AuditDetails audit = source.getAuditDetails();
        return Identifier.builder()
                .id(source.getId())
                .clientReferenceId(source.getClientReferenceId())
                .individualId(source.getIndividualId())
                .individualClientReferenceId(source.getIndividualClientReferenceId())
                .identifierType(source.getIdentifierType())
                .identifierId(source.getIdentifierId())
                .isDeleted(source.getIsDeleted())
                .auditDetails(audit == null ? null : AuditDetails.builder()
                        .createdBy(audit.getCreatedBy())
                        .createdTime(audit.getCreatedTime())
                        .lastModifiedBy(audit.getLastModifiedBy())
                        .lastModifiedTime(audit.getLastModifiedTime())
                        .build())
                .build();
    }

    private List<String> collectIndividualIds(List<Individual> individuals) {
        return individuals.stream()
                .map(Individual::getId)
                .filter(id -> !ObjectUtils.isEmpty(id))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Only individuals carrying both keys contribute, mirroring the per-row predicate: an individual
     * without an id matched on "individualId = NULL" and so returned no identifiers.
     */
    private List<String> collectClientReferenceIds(List<Individual> individuals) {
        return individuals.stream()
                .filter(individual -> !ObjectUtils.isEmpty(individual.getId())
                        && !ObjectUtils.isEmpty(individual.getClientReferenceId()))
                .map(Individual::getClientReferenceId)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Per-individual mutable copy. The previous per-row queries always handed each individual its own
     * mutable list, and downstream enrichment mutates these lists in place.
     */
    private <T> List<T> copyOf(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static final class IdentifierIndex {
        private final Map<String, List<Identifier>> byIndividualId;
        private final Map<String, List<Identifier>> byIndividualClientReferenceId;

        private IdentifierIndex(Map<String, List<Identifier>> byIndividualId,
                                Map<String, List<Identifier>> byIndividualClientReferenceId) {
            this.byIndividualId = byIndividualId;
            this.byIndividualClientReferenceId = byIndividualClientReferenceId;
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
