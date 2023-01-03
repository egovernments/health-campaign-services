package org.egov.individual.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.producer.Producer;
import org.egov.individual.repository.rowmapper.AddressRowMapper;
import org.egov.individual.repository.rowmapper.IdentifierRowMapper;
import org.egov.individual.repository.rowmapper.IndividualRowMapper;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.Identifier;
import org.egov.individual.web.models.Individual;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
        Map<Object, Object> redisMap = this.redisTemplate.opsForHash().entries(tableName);
        List<String> foundInCache = ids.stream().filter(redisMap::containsKey).collect(Collectors.toList());
        objFound = foundInCache.stream().map(id -> (Individual) redisMap.get(id)).collect(Collectors.toList());
        log.info("Cache hit: {}", !objFound.isEmpty());
        ids.removeAll(foundInCache);
        if (ids.isEmpty()) {
            return objFound;
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

    private void enrichIndividuals(List<Individual> individuals, Boolean includeDeleted) {
        if (!individuals.isEmpty()) {
            individuals.forEach(individual -> {
                String individualAddressQuery = getQuery("SELECT ia.addressId FROM individual_address ia WHERE ia.individualId =:individualId",
                        includeDeleted);
                String addressQuery = String.format("SELECT * FROM address WHERE id IN (%s)", individualAddressQuery);
                Map<String, Object> indServerGenIdParamMap = new HashMap<>();
                indServerGenIdParamMap.put("individualId", individual.getId());
                List<Address> addresses = this.namedParameterJdbcTemplate
                        .query(addressQuery, indServerGenIdParamMap, new AddressRowMapper());
                String individualIdentifierQuery = getQuery("SELECT * FROM individual_identifier ii WHERE ii.individualId =:individualId",
                        includeDeleted);
                List<Identifier> identifiers = this.namedParameterJdbcTemplate
                        .query(individualIdentifierQuery, indServerGenIdParamMap,
                                new IdentifierRowMapper());
                individual.setAddress(addresses);
                individual.setIdentifiers(identifiers);
            });
        }
    }

    private String getQuery(String baseQuery, Boolean includeDeleted) {
        StringBuilder baseQueryBuilder = new StringBuilder(baseQuery);
        if (null != includeDeleted && includeDeleted) {
            return baseQuery;
        } else {
            baseQueryBuilder.append("AND isDeleted = false");
        }
        return baseQueryBuilder.toString();
    }
}
