package org.egov.individual.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.producer.Producer;
import org.egov.individual.repository.rowmapper.AddressRowMapper;
import org.egov.individual.repository.rowmapper.IdentifierRowMapper;
import org.egov.individual.repository.rowmapper.IndividualRowMapper;
import org.egov.individual.repository.rowmapper.SkillRowMapper;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.Identifier;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualSearch;
import org.egov.individual.web.models.Skill;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.sql.Date;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdMethod;

@Repository
@Slf4j
public class IndividualRepository extends GenericRepository<Individual> {

    protected IndividualRepository(Producer producer,
                                   NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                   RedisTemplate<String, Object> redisTemplate,
                                   SelectQueryBuilder selectQueryBuilder,
                                   IndividualRowMapper individualRowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate,
                selectQueryBuilder, individualRowMapper, Optional.of("individual"));
    }

    public List<Individual> findById(List<String> ids, String idColumn, Boolean includeDeleted) {
        List<Individual> objFound;
        objFound = findInCache(ids).stream()
                .filter(individual -> individual.getIsDeleted().equals(includeDeleted))
                .collect(Collectors.toList());
        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, idColumn);
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .collect(Collectors.toList()));
            if (ids.isEmpty()) {
                return objFound;
            }
        }

        String individualQuery = String.format(getQuery("SELECT * FROM individual WHERE %s IN (:ids)",
                includeDeleted), idColumn);
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);
        List<Individual> individuals = this.namedParameterJdbcTemplate
                .query(individualQuery, paramMap, this.rowMapper);
        enrichIndividuals(individuals, includeDeleted);
        objFound.addAll(individuals);
        putInCache(objFound);
        return objFound;
    }

    public List<Individual> find(IndividualSearch searchObject, Integer limit, Integer offset,
                                 String tenantId, Long lastChangedSince, Boolean includeDeleted) {
        Map<String, Object> paramsMap = new HashMap<>();
        String query = getQueryForIndividual(searchObject, limit, offset, tenantId, lastChangedSince,
                includeDeleted, paramsMap);
        if (searchObject.getIdentifier() == null) {
            List<Individual> individuals = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);
            if (!individuals.isEmpty()) {
                enrichIndividuals(individuals, includeDeleted);
            }
            return individuals;
        } else {
            Map<String, Object> identifierParamMap = new HashMap<>();
            String identifierQuery = getIdentifierQuery(searchObject.getIdentifier(), identifierParamMap);
            identifierParamMap.put("isDeleted", includeDeleted);
            List<Identifier> identifiers = this.namedParameterJdbcTemplate
                    .query(identifierQuery, identifierParamMap, new IdentifierRowMapper());
            if (!identifiers.isEmpty()) {
                query = query.replace(" tenantId=:tenantId ", " tenantId=:tenantId AND id=:individualId ");
                paramsMap.put("individualId", identifiers.stream().findAny().get().getIndividualId());
                List<Individual> individuals = this.namedParameterJdbcTemplate.query(query,
                        paramsMap, this.rowMapper);
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
                return individuals;
            }
            return Collections.emptyList();
        }
    }

    private void enrichSkills(Boolean includeDeleted, Individual individual, Map<String, Object> indServerGenIdParamMap) {
        String individualSkillQuery = getQuery("SELECT * FROM individual_skill WHERE individualId =:individualId",
                includeDeleted);
        List<Skill> skills = this.namedParameterJdbcTemplate.query(individualSkillQuery, indServerGenIdParamMap,
                new SkillRowMapper());
        individual.setSkills(skills);
    }

    private String getQueryForIndividual(IndividualSearch searchObject, Integer limit, Integer offset,
                                         String tenantId, Long lastChangedSince,
                                         Boolean includeDeleted, Map<String, Object> paramsMap) {
        String query = "SELECT * FROM individual";
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject, QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();

        query += " AND tenantId=:tenantId ";
        if (query.contains(tableName + " AND")) {
            query = query.replace(tableName + " AND", tableName + " WHERE ");
        }
        if (searchObject.getGender() != null) {
            query = query + "AND gender =:gender ";
            paramsMap.put("gender", searchObject.getGender().name());
        }
        if (searchObject.getDateOfBirth() != null) {
            query = query + "AND dateOfBirth =:dateOfBirth ";
            paramsMap.put("dateOfBirth", Date.valueOf(searchObject.getDateOfBirth()));
        }
        if (Boolean.FALSE.equals(includeDeleted)) {
            query = query + "AND isDeleted=:isDeleted ";
        }

        if (lastChangedSince != null) {
            query = query + "AND lastModifiedTime>=:lastModifiedTime ";
        }
        query = query + "ORDER BY id ASC LIMIT :limit OFFSET :offset";
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        return query;
    }

    private String getIdentifierQuery(Identifier identifier, Map<String, Object> paramMap) {
        String identifierQuery = "SELECT * FROM individual_identifier";
        List<String> identifierWhereFields = GenericQueryBuilder.getFieldsWithCondition(identifier,
                QueryFieldChecker.isNotNull, paramMap);
        return GenericQueryBuilder.generateQuery(identifierQuery, identifierWhereFields).toString();
    }

    private List<Address> getAddressForIndividual(String individualId, Boolean includeDeleted) {
        String addressQuery = getQuery("SELECT a.*, ia.individualId, ia.createdBy, ia.lastModifiedBy, ia.createdTime, ia.lastModifiedTime, ia.isDeleted FROM address a, individual_address ia WHERE a.id = ia.addressId and ia.individualId =:individualId", includeDeleted, "ia");
        Map<String, Object> indServerGenIdParamMap = new HashMap<>();
        indServerGenIdParamMap.put("individualId", individualId);
        indServerGenIdParamMap.put("isDeleted", includeDeleted);
        return this.namedParameterJdbcTemplate
                .query(addressQuery, indServerGenIdParamMap, new AddressRowMapper());
    }

    private void enrichIndividuals(List<Individual> individuals, Boolean includeDeleted) {
        if (!individuals.isEmpty()) {
            individuals.forEach(individual -> {
                Map<String, Object> indServerGenIdParamMap = new HashMap<>();
                indServerGenIdParamMap.put("individualId", individual.getId());
                indServerGenIdParamMap.put("isDeleted", includeDeleted);
                List<Address> addresses = getAddressForIndividual(individual.getId(), includeDeleted);
                String individualIdentifierQuery = getQuery("SELECT * FROM individual_identifier ii WHERE ii.individualId =:individualId",
                        includeDeleted);
                List<Identifier> identifiers = this.namedParameterJdbcTemplate
                        .query(individualIdentifierQuery, indServerGenIdParamMap,
                                new IdentifierRowMapper());
                enrichSkills(includeDeleted, individual, indServerGenIdParamMap);
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
