package org.egov.hrms.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.hrms.utils.ErrorConstants;
import org.egov.hrms.utils.HRMSUtils;
import org.egov.hrms.web.contract.EmployeeCountResponse;
import org.egov.hrms.web.contract.EmployeeSearchCriteria;
import org.egov.tracer.model.CustomException;
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

	@Autowired
	private MultiStateInstanceUtil multiStateInstanceUtil;

	/**
	 * DB Repository that makes jdbc calls to the db and fetches employees.
	 * 
	 * @param criteria
	 * @param requestInfo
	 * @return
	 */
	public Map<String, Object> fetchEmployees(EmployeeSearchCriteria criteria, RequestInfo requestInfo){
		Long totalCount = 0L;
		// Get the tenantId from the criteria
		String tenantId = criteria.getTenantId();
		Long totalCount = 0L;
		List<Employee> employees = new ArrayList<>();
		List<Object> preparedStmtList = new ArrayList<>();
		List<Object> preparedStmtListCount = new ArrayList<>();
		if (hrmsUtils.isAssignmentSearchReqd(criteria)) {
			List<String> empUuids = fetchEmployeesforAssignment(criteria, requestInfo);
			if (CollectionUtils.isEmpty(empUuids))
				return new EmployeeCountResponse(employees, 0);
			else {
				if (!CollectionUtils.isEmpty(criteria.getUuids()))
					criteria.setUuids(
							criteria.getUuids().stream().filter(empUuids::contains).collect(Collectors.toList()));
				else
					criteria.setUuids(empUuids);
			}
		}
		if (criteria.getIncludeUnassigned() != null && criteria.getIncludeUnassigned()) {
			List<String> empUuids = fetchUnassignedEmployees(criteria, requestInfo);
			criteria.setUuids(empUuids);
		}

		// Build queries for employee search
		String query = queryBuilder.getEmployeeSearchQuery(criteria, preparedStmtList, true);
		String queryWithOutLimitAndOffset = queryBuilder.getEmployeeSearchQuery(criteria, preparedStmtListWithOutLimitAndOffset , false);
		// Wrap query construction in try-catch to handle invalid tenant scenarios gracefully
		try {
			query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
			queryWithOutLimitAndOffset = multiStateInstanceUtil.replaceSchemaPlaceholder(queryWithOutLimitAndOffset, tenantId);
		} catch (InvalidTenantIdException e) {
			throw new CustomException(ErrorConstants.TENANT_ID_EXCEPTION, e.getMessage());
		}

		// Excecute query
		try {
			employees = jdbcTemplate.query(query, preparedStmtList.toArray(),rowMapper);
			totalCount = jdbcTemplate.query(queryWithOutLimitAndOffset, preparedStmtList.toArray(),rowMapper).spliterator().getExactSizeIfKnown();
		}catch(Exception e) {
			log.error("Exception while making the db call: ",e);
			log.error("query; "+query);
		}
		return new EmployeeCountResponse(employees, totalCount.intValue());
	}

	private List<String> fetchUnassignedEmployees(EmployeeSearchCriteria criteria, RequestInfo requestInfo) {
		List<String> employeesIds = new ArrayList<>();
		List <Object> preparedStmtList = new ArrayList<>();
		// Get the tenantId from the criteria
		String tenantId = criteria.getTenantId();
		String query = queryBuilder.getUnassignedEmployeesSearchQuery(criteria, preparedStmtList);
		// Wrap query construction in try-catch to handle invalid tenant scenarios gracefully
		try {
			query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
		} catch (InvalidTenantIdException e) {
			throw new CustomException(ErrorConstants.TENANT_ID_EXCEPTION, e.getMessage());
		}
		try {
			employeesIds = jdbcTemplate.queryForList(query, preparedStmtList.toArray(), String.class);
		} catch (Exception e) {
			log.error("Exception while making the db call: ", e);
			log.error("query; " + query);
		}
		return employeesIds;
	}

	private List<String> fetchEmployeesforAssignment(EmployeeSearchCriteria criteria, RequestInfo requestInfo) {
		List<String> employeesIds = new ArrayList<>();
		List <Object> preparedStmtList = new ArrayList<>();
		// Get the tenantId from the criteria
		String tenantId = criteria.getTenantId();
		String query = queryBuilder.getAssignmentSearchQuery(criteria, preparedStmtList);
		// Wrap query construction in try-catch to handle invalid tenant scenarios gracefully
		try {
			query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
		} catch (InvalidTenantIdException e) {
			throw new CustomException(ErrorConstants.TENANT_ID_EXCEPTION, e.getMessage());
		}

		try {

			employeesIds = jdbcTemplate.queryForList(query, preparedStmtList.toArray(), String.class);
		} catch (Exception e) {
			log.error("Exception while making the db call: ", e);
			log.error("query; " + query);
		}
		return employeesIds;

	}

	/**
	 * DB Repository that makes jdbc calls to the db and fetches employee count.
	 *
	 * @param tenantId
	 * @return
	 */
	public Long fetchPosition(String tenantId) {
		String query = queryBuilder.getPositionSeqQuery();
		Long id = null;
		// Wrap query construction in try-catch to handle invalid tenant scenarios gracefully
		try {
			query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
		} catch (InvalidTenantIdException e) {
			throw new CustomException(ErrorConstants.TENANT_ID_EXCEPTION, e.getMessage());
		}
		try {
			id = jdbcTemplate.queryForObject(query, Long.class);
		} catch (Exception e) {
			log.error("Exception while making the db call: ", e);
			log.error("query; " + query);
		}
		return id;
	}

	/**
	 * DB Repository that makes jdbc calls to the db and fetches employee count.
	 *
	 * @param tenantId
	 * @return
	 */
	public Map<String, String> fetchEmployeeCount(String tenantId) {
		Map<String, String> response = new HashMap<>();
		List<Object> preparedStmtList = new ArrayList<>();

		String query = queryBuilder.getEmployeeCountQuery(tenantId, preparedStmtList);
		log.info("query; "+query);
		// Wrap query construction in try-catch to handle invalid tenant scenarios gracefully
        try {
			query = multiStateInstanceUtil.replaceSchemaPlaceholder( query, tenantId);
        } catch (InvalidTenantIdException e) {
            throw new CustomException(ErrorConstants.TENANT_ID_EXCEPTION, e.getMessage());
        }
        try {
			response=jdbcTemplate.query(query, preparedStmtList.toArray(),countRowMapper);
		}catch(Exception e) {
			log.error("Exception while making the db call: ",e);
			log.error("query; "+query);
		}
		return response;
	}

}
