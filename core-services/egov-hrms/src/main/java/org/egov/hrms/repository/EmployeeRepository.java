package org.egov.hrms.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.egov.common.contract.request.RequestInfo;
import org.egov.hrms.utils.HRMSUtils;
import org.egov.hrms.web.contract.EmployeeCountResponse;
import org.egov.hrms.web.contract.EmployeeSearchCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.extern.slf4j.Slf4j;

import org.egov.hrms.model.Employee;
import org.springframework.util.CollectionUtils;

@Repository
@Slf4j
public class EmployeeRepository {
	
	@Autowired
	private EmployeeQueryBuilder queryBuilder;
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	@Autowired
	private EmployeeRowMapper rowMapper;

	@Autowired
	private EmployeeCountRowMapper countRowMapper;

	@Autowired
	private HRMSUtils hrmsUtils;
	
	/**
	 * DB Repository that makes jdbc calls to the db and fetches employees.
	 * 
	 * @param criteria
	 * @param requestInfo
	 * @return
	 */
	public EmployeeCountResponse fetchEmployees(EmployeeSearchCriteria criteria, RequestInfo requestInfo){
		List<Employee> employees = new ArrayList<>();
		List<Object> preparedStmtList = new ArrayList<>();
		if(hrmsUtils.isAssignmentSearchReqd(criteria)) {
			List<String> empUuids = fetchEmployeesforAssignment(criteria, requestInfo);
			if (CollectionUtils.isEmpty(empUuids))
				return new EmployeeCountResponse(employees, 0);
			else {
				if(!CollectionUtils.isEmpty(criteria.getUuids()))
					criteria.setUuids(criteria.getUuids().stream().filter(empUuids::contains).collect(Collectors.toList()));
				else
					criteria.setUuids(empUuids);
			}
		}
		if (criteria.getIncludeUnassigned() != null && criteria.getIncludeUnassigned()) {
			List<String> empUuids = fetchUnassignedEmployees(criteria, requestInfo);
			criteria.setUuids(empUuids);
		}
		List<Employee> employeesCount = new ArrayList<>();
		String queryCount = queryBuilder.getEmployeeSearchQueryWithoutPagination(criteria, preparedStmtList);
		int count=0;
		try {
			employeesCount = jdbcTemplate.query(queryCount, preparedStmtList.toArray(),rowMapper);
			 count = employeesCount.size();
		}catch(Exception e) {
			log.error("Exception while making the db call: ",e);
			log.error("query; "+queryCount);
		}

		String query = queryBuilder.getEmployeeSearchQuery(criteria, preparedStmtList);
		try {
			employees = jdbcTemplate.query(query, preparedStmtList.toArray(),rowMapper);
		}catch(Exception e) {
			log.error("Exception while making the db call: ",e);
			log.error("query; "+query);
		}
		return new EmployeeCountResponse(employees, count);
	}

	private List<String> fetchUnassignedEmployees(EmployeeSearchCriteria criteria, RequestInfo requestInfo) {
		List<String> employeesIds = new ArrayList<>();
		List <Object> preparedStmtList = new ArrayList<>();
		String query = queryBuilder.getUnassignedEmployeesSearchQuery(criteria, preparedStmtList);
		try {
			employeesIds = jdbcTemplate.queryForList(query, preparedStmtList.toArray(),String.class);
		}catch(Exception e) {
			log.error("Exception while making the db call: ",e);
			log.error("query; "+query);
		}
		return employeesIds;
	}

	private List<String> fetchEmployeesforAssignment(EmployeeSearchCriteria criteria, RequestInfo requestInfo) {
		List<String> employeesIds = new ArrayList<>();
		List <Object> preparedStmtList = new ArrayList<>();
		String query = queryBuilder.getAssignmentSearchQuery(criteria, preparedStmtList);
		try {

			employeesIds = jdbcTemplate.queryForList(query, preparedStmtList.toArray(),String.class);
		}catch(Exception e) {
			log.error("Exception while making the db call: ",e);
			log.error("query; "+query);
		}
		return employeesIds;

	}

	/**
	 * Fetches next value in the position seq table
	 * 
	 * @return
	 */
	public Long fetchPosition(){
		String query = queryBuilder.getPositionSeqQuery();
		Long id = null;
		try {
			id = jdbcTemplate.queryForObject(query, Long.class);
		}catch(Exception e) {
			log.error("Exception while making the db call: ",e);
			log.error("query; "+query);
		}
		return id;
	}

	/**
	 * DB Repository that makes jdbc calls to the db and fetches employee count.
	 *
	 * @param tenantId
	 * @return
	 */
	public Map<String,String> fetchEmployeeCount(String tenantId){
		Map<String,String> response = new HashMap<>();
		List<Object> preparedStmtList = new ArrayList<>();

		String query = queryBuilder.getEmployeeCountQuery(tenantId, preparedStmtList);
		log.info("query; "+query);
		try {
			response=jdbcTemplate.query(query, preparedStmtList.toArray(),countRowMapper);
		}catch(Exception e) {
			log.error("Exception while making the db call: ",e);
			log.error("query; "+query);
		}
		return response;
	}

}
