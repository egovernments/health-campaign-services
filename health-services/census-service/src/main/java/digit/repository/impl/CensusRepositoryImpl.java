package digit.repository.impl;

import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.CensusRepository;
import digit.repository.querybuilder.CensusQueryBuilder;
import digit.repository.rowmapper.CensusRowMapper;
import digit.repository.rowmapper.StatusCountRowMapper;
import digit.util.CommonUtil;
import digit.web.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static digit.config.ServiceConstants.CENSUS_BUSINESS_SERVICE;

@Slf4j
@Repository
public class CensusRepositoryImpl implements CensusRepository {

    private Producer producer;

    private Configuration config;

    private CensusQueryBuilder queryBuilder;

    private CensusRowMapper censusRowMapper;

    private JdbcTemplate jdbcTemplate;

    private StatusCountRowMapper statusCountRowMapper;

    private CommonUtil commonUtil;

    public CensusRepositoryImpl(Producer producer, Configuration config, CensusQueryBuilder queryBuilder, CensusRowMapper censusRowMapper, JdbcTemplate jdbcTemplate, StatusCountRowMapper statusCountRowMapper,CommonUtil commonUtil) {
        this.producer = producer;
        this.config = config;
        this.queryBuilder = queryBuilder;
        this.censusRowMapper = censusRowMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.statusCountRowMapper = statusCountRowMapper;
        this.commonUtil = commonUtil;
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

        if(censusSearchCriteria.getAreaCodes() != null && censusSearchCriteria.getAreaCodes().isEmpty())
            return new ArrayList<>();

        // Fetch census ids from database
        List<String> censusIds = queryDatabaseForCensusIds(censusSearchCriteria);

        // Return empty list back as response if no census ids are found
        if(CollectionUtils.isEmpty(censusIds)) {
            log.info("No census ids found for provided census search criteria.");
            return new ArrayList<>();
        }

        // Fetch census from database based on the acquired ids
        return searchCensusByIds(censusIds);
    }

    private List<Census> searchCensusByIds(List<String> censusIds) {

        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getCensusQuery(censusIds, preparedStmtList);
        log.info("Census query: " + query);
        return jdbcTemplate.query(query, censusRowMapper, preparedStmtList.toArray());

    }

    private List<String> queryDatabaseForCensusIds(CensusSearchCriteria censusSearchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getCensusSearchQuery(censusSearchCriteria, preparedStmtList);
        log.info("Census search query: " + query);
        return jdbcTemplate.query(query, new SingleColumnRowMapper<>(String.class), preparedStmtList.toArray());
    }

    /**
     * Counts the number of census records based on the provided search criteria.
     *
     * @param censusSearchCriteria The search criteria for filtering census records.
     * @return The total count of census matching the search criteria.
     */
    @Override
    public Integer count(CensusSearchCriteria censusSearchCriteria) {

        if(censusSearchCriteria.getAreaCodes() != null && censusSearchCriteria.getAreaCodes().isEmpty())
            return 0;

        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getCensusCountQuery(censusSearchCriteria, preparedStmtList);

        return jdbcTemplate.queryForObject(query, preparedStmtList.toArray(), Integer.class);
    }

    /**
     * Counts the census record based on their current status for the provided search criteria.
     *
     * @param censusSearchRequest The request with search criteria for filtering census records.
     * @return The status count of census records for the given search criteria.
     */
    @Override
    public Map<String, Integer> statusCount(CensusSearchRequest censusSearchRequest) {
        List<Object> preparedStmtList = new ArrayList<>();
        List<String> statusList = commonUtil.getStatusFromBusinessService(censusSearchRequest.getRequestInfo(), CENSUS_BUSINESS_SERVICE, censusSearchRequest.getCensusSearchCriteria().getTenantId());

        String query = queryBuilder.getCensusStatusCountQuery(censusSearchRequest.getCensusSearchCriteria(), preparedStmtList);
        Map<String, Integer> statusCountMap = jdbcTemplate.query(query, statusCountRowMapper, preparedStmtList.toArray());

        statusList.forEach(status -> {
            if(ObjectUtils.isEmpty(statusCountMap.get(status)))
                statusCountMap.put(status, 0);
        });

        return statusCountMap;
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
     * Updates workflow status of a list of census records.
     *
     * @param request The bulk request containing the census records.
     */
    @Override
    public void bulkUpdate(BulkCensusRequest request) {
        // Get bulk census update query
        String bulkCensusUpdateQuery = queryBuilder.getBulkCensusQuery();

        // Prepare rows for bulk update
        List<Object[]> rows = request.getCensus().stream().map(census -> new Object[] {
                    census.getStatus(),
                    census.getAssignee(),
                    census.getAuditDetails().getLastModifiedBy(),
                    census.getAuditDetails().getLastModifiedTime(),
                    commonUtil.convertToPgObject(census.getAdditionalDetails()),
                    census.getFacilityAssigned(),
                    census.getId()
        }).toList();

        // Perform bulk update
        jdbcTemplate.batchUpdate(bulkCensusUpdateQuery, rows);
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
                .status(census.getStatus())
                .type(census.getType().toString())
                .totalPopulation(census.getTotalPopulation())
                .populationByDemographics(census.getPopulationByDemographics())
                .additionalFields(census.getAdditionalFields())
                .effectiveFrom(census.getEffectiveFrom())
                .effectiveTo(census.getEffectiveTo())
                .source(census.getSource())
                .boundaryAncestralPath(census.getBoundaryAncestralPath().get(0))
                .facilityAssigned(census.getFacilityAssigned())
                .additionalDetails(census.getAdditionalDetails())
                .auditDetails(census.getAuditDetails())
                .build();

        return CensusRequestDTO.builder()
                .requestInfo(censusRequest.getRequestInfo())
                .censusDTO(censusDTO)
                .build();
    }
}
