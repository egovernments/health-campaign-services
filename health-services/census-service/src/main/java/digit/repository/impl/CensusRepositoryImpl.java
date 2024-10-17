package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.CensusRepository;
import digit.repository.querybuilder.CensusQueryBuilder;
import digit.repository.rowmapper.CensusRowMapper;
import digit.repository.rowmapper.StatusCountRowMapper;
import digit.web.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class CensusRepositoryImpl implements CensusRepository {

    private Producer producer;

    private Configuration config;

    private CensusQueryBuilder queryBuilder;

    private CensusRowMapper censusRowMapper;

    private JdbcTemplate jdbcTemplate;

    private StatusCountRowMapper statusCountRowMapper;

    public CensusRepositoryImpl(Producer producer, Configuration config, CensusQueryBuilder queryBuilder, CensusRowMapper censusRowMapper, JdbcTemplate jdbcTemplate, StatusCountRowMapper statusCountRowMapper) {
        this.producer = producer;
        this.config = config;
        this.queryBuilder = queryBuilder;
        this.censusRowMapper = censusRowMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.statusCountRowMapper = statusCountRowMapper;
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
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getCensusQuery(censusSearchCriteria, preparedStmtList);

        return jdbcTemplate.query(query, censusRowMapper, preparedStmtList.toArray());
    }

    /**
     * Counts the number of census records based on the provided search criteria.
     *
     * @param censusSearchCriteria The search criteria for filtering census records.
     * @return The total count of census matching the search criteria.
     */
    @Override
    public Integer count(CensusSearchCriteria censusSearchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getCensusCountQuery(censusSearchCriteria, preparedStmtList);

        return jdbcTemplate.queryForObject(query, preparedStmtList.toArray(), Integer.class);
    }

    /**
     * Counts the census record based on their current status for the provided search criteria.
     *
     * @param censusSearchCriteria The search criteria for filtering census records.
     * @return The status count of census records for the given search criteria.
     */
    @Override
    public Map<String, Integer> statusCount(CensusSearchCriteria censusSearchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getCensusStatusCountQuery(censusSearchCriteria, preparedStmtList);

        return jdbcTemplate.query(query, statusCountRowMapper, preparedStmtList.toArray());
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
                .boundaryAncestralPath(census.getBoundaryAncestralPath().get(0))
                .additionalDetails(census.getAdditionalDetails())
                .auditDetails(census.getAuditDetails())
                .build();

        return CensusRequestDTO.builder()
                .requestInfo(censusRequest.getRequestInfo())
                .censusDTO(censusDTO)
                .build();
    }
}
