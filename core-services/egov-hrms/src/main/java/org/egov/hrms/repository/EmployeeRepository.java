package org.egov.hrms.repository;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.egov.common.contract.request.RequestInfo;
import org.egov.hrms.model.*;
import org.egov.hrms.utils.HRMSUtils;
import org.egov.hrms.web.contract.EmployeeSearchCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.util.CollectionUtils;

import static org.egov.hrms.repository.EmployeeQueries.*;

@Repository
@Slf4j
public class EmployeeRepository {
	
	@Autowired
	private EmployeeQueryBuilder queryBuilder;
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	@Autowired
	private EmployeeTableRowMapper employeeTableRowMapper;

	@Autowired DepartmentalTestRowMapper departmentalTestRowMapper;

	@Autowired
	private JurisdictionRowMapper jurisdictionRowMapper;

	@Autowired
	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	@Autowired
	private AssignmentRowMapper assignmentRowMapper;

	@Autowired
	private EducationalDetailsRowMapper educationalDetailsRowMapper;

	@Autowired
	private DocumentRowMapper documentRowMapper;

	@Autowired
	private ServiceHistoryRowMapper serviceHistoryRowMapper;

	@Autowired
	private DeactivationDetailsRowMapper deactivationDetailsRowMapper;

	@Autowired
	private ReactivationDetailsRowMapper reactivationDetailsRowMapper;

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
	public List<Employee> fetchEmployees(EmployeeSearchCriteria criteria, RequestInfo requestInfo){
		List<Employee> employees = new ArrayList<>();
		if(hrmsUtils.isAssignmentSearchReqd(criteria)) {
			List<String> empUuids = fetchEmployeesforAssignment(criteria, requestInfo);
			if (CollectionUtils.isEmpty(empUuids))
				return employees;
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

		return fetchEmployeesByCriteria(criteria);
	}

	List<Employee>fetchEmployeesByCriteria (EmployeeSearchCriteria criteria) {
		List<Object> preparedStmtList = new ArrayList<>();
		String query = queryBuilder.getEmployeeSearchQuery(criteria, preparedStmtList);
		List<Employee> employees = jdbcTemplate.query(query, preparedStmtList.toArray(), employeeTableRowMapper);

		if (CollectionUtils.isEmpty(employees)) {
			return employees;
		}

		List<String> employeeIds = employees.stream().map(Employee::getUuid).collect(Collectors.toList());
		MapSqlParameterSource parameters = new MapSqlParameterSource("employeeIds", employeeIds);

		List<Jurisdiction> jurisdictions = namedParameterJdbcTemplate.query(SUBQUERY_EG_HRMS_JURISDICTION, parameters, jurisdictionRowMapper);
		List<Assignment> assignments = namedParameterJdbcTemplate.query(SUBQUERY_EG_HRMS_ASSIGNMENT, parameters, assignmentRowMapper);
		List<EducationalQualification> educationalQualifications = namedParameterJdbcTemplate.query(SUBQUERY_EG_HRMS_EDUCATIONALDETAILS, parameters, educationalDetailsRowMapper);
		List<DepartmentalTest> departmentalTests = namedParameterJdbcTemplate.query(SUBQUERY_EG_HRMS_DEPARTMENTALTESTS, parameters, departmentalTestRowMapper);
		List<EmployeeDocument> employeeDocuments = namedParameterJdbcTemplate.query(SUBQUERY_EG_HRMS_EMPDOCUMENTS, parameters, documentRowMapper);
		List<ServiceHistory> serviceHistories = namedParameterJdbcTemplate.query(SUBQUERY_EG_HRMS_SERVICEHISTORY, parameters,serviceHistoryRowMapper);
		List<DeactivationDetails> deactivationDetails = namedParameterJdbcTemplate.query(SUBQUERY_EG_HRMS_DEACTIVATIONDETAILS, parameters,deactivationDetailsRowMapper);
		List<ReactivationDetails> reactivationDetails = namedParameterJdbcTemplate.query(SUBQUERY_EG_HRMS_REACTIVATIONDETAILS, parameters, reactivationDetailsRowMapper);

		return mapEmployeeData(employees, groupByEmployeeId(assignments, Assignment::getEmployeeId),
				groupByEmployeeId(jurisdictions, Jurisdiction::getEmployeeId),
				groupByEmployeeId(educationalQualifications, EducationalQualification::getEmployeeId),
				groupByEmployeeId(departmentalTests, DepartmentalTest::getEmployeeId),
				groupByEmployeeId(employeeDocuments, EmployeeDocument::getEmployeeId),
				groupByEmployeeId(serviceHistories, ServiceHistory::getEmployeeId),
				groupByEmployeeId(deactivationDetails, DeactivationDetails::getEmployeeId),
				groupByEmployeeId(reactivationDetails, ReactivationDetails::getEmployeeId));
	}

	private <T> Map<String, List<T>> groupByEmployeeId(List<T> subTableData, Function<T, String> employeeIdExtractor) {
		return subTableData.stream()
				.collect(Collectors.groupingBy(employeeIdExtractor));
	}


	public List<Employee> mapEmployeeData(List<Employee> employees,
										  Map<String, List<Assignment>> assignmentsByEmployeeId,
										  Map<String, List<Jurisdiction>> jurisdictionsByEmployeeId,
										  Map<String, List<EducationalQualification>> educationalQualificationsByEmployeeId,
										  Map<String, List<DepartmentalTest>> departmentalTestsByEmployeeId,
										  Map<String, List<EmployeeDocument>> documentsByEmployeeId,
										  Map<String, List<ServiceHistory>> serviceHistoryByEmployeeId,
										  Map<String, List<DeactivationDetails>> deactivationDetailsByEmployeeId,
										  Map<String, List<ReactivationDetails>> reactivationDetailsByEmployeeId) {
		return employees.stream()
				.map(employee -> {
					String employeeId = employee.getUuid();

					// Set departmental tests
					List<DepartmentalTest> departmentalTests = departmentalTestsByEmployeeId.getOrDefault(employeeId, Collections.emptyList());
					employee.setTests(departmentalTests);

					// Set documents
					List<EmployeeDocument> documents = documentsByEmployeeId.getOrDefault(employeeId, Collections.emptyList());
					employee.setDocuments(documents);

					// Set service history
					List<ServiceHistory> serviceHistory = serviceHistoryByEmployeeId.getOrDefault(employeeId, Collections.emptyList());
					employee.setServiceHistory(serviceHistory);

					// Set deactivation details
					List<DeactivationDetails> deactivationDetails = deactivationDetailsByEmployeeId.getOrDefault(employeeId, Collections.emptyList());
					employee.setDeactivationDetails(deactivationDetails);

					// Set reactivation details
					List<ReactivationDetails> reactivationDetails = reactivationDetailsByEmployeeId.getOrDefault(employeeId, Collections.emptyList());
					employee.setReactivationDetails(reactivationDetails);

					// Set educational qualifications
					List<EducationalQualification> educationalQualifications = educationalQualificationsByEmployeeId.getOrDefault(employeeId, Collections.emptyList());
					employee.setEducation(educationalQualifications);

					// Set assignments
					List<Assignment> assignments = assignmentsByEmployeeId.getOrDefault(employeeId, Collections.emptyList());
					employee.setAssignments(assignments);

					// Set jurisdictions
					List<Jurisdiction> jurisdictions = jurisdictionsByEmployeeId.getOrDefault(employeeId, Collections.emptyList());
					employee.setJurisdictions(jurisdictions);

					return employee;
				})
				.collect(Collectors.toList());
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
