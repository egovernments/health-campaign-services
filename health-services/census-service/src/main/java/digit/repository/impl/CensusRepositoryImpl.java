package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.CensusRepository;
import digit.repository.querybuilder.CensusQueryBuilder;
import digit.repository.rowmapper.CensusRowMapper;
import digit.web.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
public class CensusRepositoryImpl implements CensusRepository {

    private Producer producer;

    private Configuration config;

    private CensusQueryBuilder queryBuilder;

    private CensusRowMapper rowMapper;

    private JdbcTemplate jdbcTemplate;

    public CensusRepositoryImpl(Producer producer, Configuration config, CensusQueryBuilder queryBuilder, CensusRowMapper rowMapper, JdbcTemplate jdbcTemplate) {
        this.producer = producer;
        this.config = config;
        this.queryBuilder = queryBuilder;
        this.rowMapper = rowMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Pushes a new census record to persister kafka topic.
     *
     * @param censusRequest The request containing the census details
     */
    @Override
    public void create(CensusRequest censusRequest) {
        CensusRequestDTO requestDTO = convertToReqDTO(censusRequest);
        producer.push(config.getCensusCreateTopic(), requestDTO);
    }

    /**
     * Searches for census records based on the provided search criteria.
     *
     * @param censusSearchCriteria The criteria to use for searching census records.
     * @return A list of census records that match the search criteria.
     */
    @Override
    public List<Census> search(CensusSearchCriteria censusSearchCriteria) {

        // Fetch census ids from database
        List<String> censusIds = queryDatabaseForCensusIds(censusSearchCriteria);

        // Return empty list back as response if no census ids are found
        if (CollectionUtils.isEmpty(censusIds)) {
            log.info("No census ids found for provided census search criteria.");
            return new ArrayList<>();
        }

        return searchCensusByIds(censusIds);
    }

    /**
     * Helper method to search for census records based on the provided census ids.
     *
     * @param censusIds list of census ids to search for census records.
     * @return a list of census records.
     */
    private List<Census> searchCensusByIds(List<String> censusIds) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getCensusQuery(censusIds, preparedStmtList);
        log.info("Census query: " + query);
        return jdbcTemplate.query(query, rowMapper, preparedStmtList.toArray());
    }

    /**
     * Helper method to query database for census ids based on the provided search criteria.
     *
     * @param censusSearchCriteria The criteria to use for searching census records.
     * @return a list of census ids that matches the provided search criteria
     */
    private List<String> queryDatabaseForCensusIds(CensusSearchCriteria censusSearchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getCensusSearchQuery(censusSearchCriteria, preparedStmtList);
        log.info("Census search query: " + query);
        return jdbcTemplate.query(query, new SingleColumnRowMapper<>(String.class), preparedStmtList.toArray());
    }

    @Override
    public Integer count(CensusSearchCriteria censusSearchCriteria) {
        return 0;
    }

    /**
     * Pushes an updated existing census record to persister kafka topic.
     *
     * @param censusRequest The request containing the updated census details
     */
    @Override
    public void update(CensusRequest censusRequest) {
        CensusRequestDTO requestDTO = convertToReqDTO(censusRequest);
        producer.push(config.getCensusUpdateTopic(), requestDTO);
    }

    /**
     * Converts the CensusRequest to a data transfer object (DTO)
     *
     * @param censusRequest The request to be converted to DTO
     * @return a DTO for CensusRequest
     */
    private CensusRequestDTO convertToReqDTO(CensusRequest censusRequest) {
        Census census = censusRequest.getCensus();

        // Creating a new data transfer object (DTO) for Census
        CensusDTO censusDTO = CensusDTO.builder()
                .id(census.getId())
                .tenantId(census.getTenantId())
                .hierarchyType(census.getHierarchyType())
                .boundaryCode(census.getBoundaryCode())
                .assignee(census.getAssignee())
                .status(census.getStatus().toString())
                .type(census.getType().toString())
                .totalPopulation(census.getTotalPopulation())
                .populationByDemographics(census.getPopulationByDemographics())
                .effectiveFrom(census.getEffectiveFrom())
                .effectiveTo(census.getEffectiveTo())
                .source(census.getSource())
                .boundaryAncestralPath(String.join(",", census.getBoundaryAncestralPath()))
                .additionalDetails(census.getAdditionalDetails())
                .auditDetails(census.getAuditDetails())
                .build();

        return CensusRequestDTO.builder()
                .requestInfo(censusRequest.getRequestInfo())
                .censusDTO(censusDTO)
                .build();
    }
}
