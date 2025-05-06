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

    @Autowired
    public PGRRepository(PGRQueryBuilder queryBuilder, PGRRowMapper rowMapper, JdbcTemplate jdbcTemplate, MultiStateInstanceUtil multiStateInstanceUtil) {
        this.queryBuilder = queryBuilder;
        this.rowMapper = rowMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.multiStateInstanceUtil = multiStateInstanceUtil;
    }


    /**
     * searches services based on search criteria and then wraps it into serviceWrappers
     * @param criteria
     * @return
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
     * searches services based on search criteria
     * @param criteria
     * @return
     */
    public List<Service> getServices(RequestSearchCriteria criteria) throws InvalidTenantIdException {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getPGRSearchQuery(criteria, preparedStmtList);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, criteria.getTenantId());
        List<Service> services =  jdbcTemplate.query(query, preparedStmtList.toArray(), rowMapper);
        return services;
    }

    /**
     * Returns the count based on the search criteria
     * @param criteria
     * @return
     */
    public Integer getCount(RequestSearchCriteria criteria) throws InvalidTenantIdException {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getCountQuery(criteria, preparedStmtList);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, criteria.getTenantId());
        Integer count =  jdbcTemplate.queryForObject(query, preparedStmtList.toArray(), Integer.class);
        return count;
    }


	public Map<String, Integer> fetchDynamicData(String tenantId) throws InvalidTenantIdException {
		List<Object> preparedStmtListCompalintsResolved = new ArrayList<>();
		String query = queryBuilder.getResolvedComplaints(tenantId,preparedStmtListCompalintsResolved );
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
		int complaintsResolved = jdbcTemplate.queryForObject(query,preparedStmtListCompalintsResolved.toArray(),Integer.class);

		List<Object> preparedStmtListAverageResolutionTime = new ArrayList<>();
		query = queryBuilder.getAverageResolutionTime(tenantId, preparedStmtListAverageResolutionTime);

        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
		int averageResolutionTime = jdbcTemplate.queryForObject(query, preparedStmtListAverageResolutionTime.toArray(),Integer.class);

		Map<String, Integer> dynamicData = new HashMap<String,Integer>();
		dynamicData.put(PGRConstants.COMPLAINTS_RESOLVED, complaintsResolved);
		dynamicData.put(PGRConstants.AVERAGE_RESOLUTION_TIME, averageResolutionTime);

		return dynamicData;
	}



}
