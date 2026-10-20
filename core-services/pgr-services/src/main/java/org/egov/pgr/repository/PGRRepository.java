package org.egov.pgr.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.pgr.repository.rowmapper.PGRQueryBuilder;
import org.egov.pgr.repository.rowmapper.PGRRowMapper;
import org.egov.pgr.util.PGRConstants;
import org.egov.pgr.web.models.RequestSearchCriteria;
import org.egov.pgr.web.models.Service;
import org.egov.pgr.web.models.ServiceWrapper;
import org.egov.pgr.web.models.Workflow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@Slf4j
public class PGRRepository {


    private final PGRQueryBuilder queryBuilder;

    private final PGRRowMapper rowMapper;

    private final JdbcTemplate jdbcTemplate;

    private final MultiStateInstanceUtil multiStateInstanceUtil;

    /**
     * Constructs a PGRRepository with the required query builder, row mapper, JDBC template,
     * and multi-state instance utility.
     *
     * @param queryBuilder the PGRQueryBuilder used for building database queries
     * @param rowMapper the PGRRowMapper used for mapping rows from the database
     * @param jdbcTemplate the JdbcTemplate used for executing SQL queries
     * @param multiStateInstanceUtil the MultiStateInstanceUtil used for multi-state specific operations
     */
    @Autowired
    public PGRRepository(PGRQueryBuilder queryBuilder, PGRRowMapper rowMapper, JdbcTemplate jdbcTemplate, MultiStateInstanceUtil multiStateInstanceUtil) {
        this.queryBuilder = queryBuilder;
        this.rowMapper = rowMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.multiStateInstanceUtil = multiStateInstanceUtil;
    }


    /**
     * Retrieves a list of ServiceWrapper objects based on the specified search criteria.
     * Each ServiceWrapper contains a Service object and its associated Workflow.
     *
     * @param criteria the search criteria used to retrieve the services. It contains
     *                 various filters such as tenantId, serviceCode, applicationStatus, etc.
     * @return a list of ServiceWrapper objects containing services and their associated workflows
     * @throws InvalidTenantIdException if the tenant ID specified in the criteria is invalid
     */
    public List<ServiceWrapper> getServiceWrappers(RequestSearchCriteria criteria) throws InvalidTenantIdException {
        List<Service> services = getServices(criteria);
        List<String> serviceRequestids = services.stream().map(Service::getServiceRequestId).collect(Collectors.toList());
        Map<String, Workflow> idToWorkflowMap = new HashMap<>();
        List<ServiceWrapper> serviceWrappers = new ArrayList<>();

        for(Service service : services){
            ServiceWrapper serviceWrapper = ServiceWrapper.builder().service(service).workflow(idToWorkflowMap.get(service.getServiceRequestId())).build();
            serviceWrappers.add(serviceWrapper);
        }
        return serviceWrappers;
    }

    /**
     * Retrieves a list of Service objects based on the specified search criteria.
     *
     * @param criteria the search criteria used to filter and retrieve the services. It may include parameters like tenantId,
     *                 application status, locality, and other relevant filters.
     * @return a list of Service objects that match the specified search criteria.
     * @throws InvalidTenantIdException if the tenant ID provided in the criteria is invalid.
     */
    public List<Service> getServices(RequestSearchCriteria criteria) throws InvalidTenantIdException {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getPGRSearchQuery(criteria, preparedStmtList);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, criteria.getTenantId());
        List<Service> services =  jdbcTemplate.query(query, preparedStmtList.toArray(), rowMapper);
        return services;
    }

    /**
     * Retrieves the count of records based on the specified search criteria.
     *
     * @param criteria the search criteria containing various filters such as tenantId,
     *                 serviceCode, applicationStatus, locality, and more, used
     *                 for constructing the query to count matching records.
     * @return the total count of records that match the provided search criteria.
     * @throws InvalidTenantIdException if the tenant ID specified in the criteria is invalid.
     */
    public Integer getCount(RequestSearchCriteria criteria) throws InvalidTenantIdException {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getCountQuery(criteria, preparedStmtList);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, criteria.getTenantId());
        Integer count =  jdbcTemplate.queryForObject(query, preparedStmtList.toArray(), Integer.class);
        return count;
    }

    /**
     * Fetches dynamic data for a given tenant, including the count of resolved complaints
     * and the average resolution time.
     *
     * @param tenantId the tenant identifier for which dynamic data is to be fetched
     * @return a map containing dynamic data where keys represent data types (e.g., complaints resolved,
     *         average resolution time) and their corresponding values as integers
     * @throws InvalidTenantIdException if the provided tenant ID is invalid
     */
	public Map<String, Integer> fetchDynamicData(String tenantId) throws InvalidTenantIdException {
		List<Object> preparedStmtListCompalintsResolved = new ArrayList<>();
		String query = queryBuilder.getResolvedComplaints(tenantId,preparedStmtListCompalintsResolved );
        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
		int complaintsResolved = jdbcTemplate.queryForObject(query,preparedStmtListCompalintsResolved.toArray(),Integer.class);

		List<Object> preparedStmtListAverageResolutionTime = new ArrayList<>();
		query = queryBuilder.getAverageResolutionTime(tenantId, preparedStmtListAverageResolutionTime);
        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
		int averageResolutionTime = jdbcTemplate.queryForObject(query, preparedStmtListAverageResolutionTime.toArray(),Integer.class);

		Map<String, Integer> dynamicData = new HashMap<String,Integer>();
		dynamicData.put(PGRConstants.COMPLAINTS_RESOLVED, complaintsResolved);
		dynamicData.put(PGRConstants.AVERAGE_RESOLUTION_TIME, averageResolutionTime);

		return dynamicData;
	}



}
