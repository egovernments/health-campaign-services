package org.egov.hrms.repository;

import org.springframework.stereotype.Component;

@Component
public class EmployeeQueries {

	public static final String HRMS_GET_EMPLOYEES = "SELECT employee.id as employee_id, employee.uuid as employee_uuid, employee.code as employee_code, "
			+ "employee.dateOfAppointment as employee_doa, employee.employeestatus as employee_status, employeetype as employee_type, employee.active as employee_active, employee.reactivateemployee as employee_reactive, "
			+ "employee.tenantid as employee_tenantid, employee.createdby as employee_createdby, employee.createddate as employee_createddate, "
			+ "employee.lastmodifiedby as employee_lastmodifiedby, employee.lastmodifieddate as employee_lastmodifieddate, assignment.uuid as assignment_uuid, "
			+ "assignment.position as assignment_position, assignment.department as assignment_department, assignment.designation as assignment_designation, "
			+ "assignment.fromdate as assignment_fromdate, assignment.todate as assignment_todate, assignment.govtordernumber as assignment_govtordernumber, "
			+ "assignment.reportingto as assignment_reportingto, assignment.ishod as assignment_ishod, assignment.iscurrentassignment as assignment_iscurrentassignment, "
			+ "assignment.tenantid as assignment_tenantid, "
			+ "assignment.createdby as assignment_createdby, assignment.createddate as assignment_createddate, assignment.lastmodifiedby as assignment_lastmodifiedby, "
			+ "assignment.lastmodifieddate as assignment_lastmodifieddate, education.uuid as education_uuid, education.qualification as education_qualification, "
			+ "education.stream as education_stream, education.yearofpassing as education_yearofpassing, education.university as education_university, "
			+ "education.remarks as education_remarks,education.isactive as education_isactive ,education.tenantid as education_tenantid, education.createdby as education_createdby, "
			+ "education.createddate as education_createddate, education.lastmodifiedby as education_lastmodifiedby, education.lastmodifieddate as education_lastmodifieddate, "
			+ "depttest.uuid as depttest_uuid, depttest.test as depttest_test, depttest.yearofpassing as depttest_yearofpassing, depttest.remarks as depttest_remarks, "
			+ "depttest.isactive as depttest_isactive, depttest.tenantid as depttest_tenantid, depttest.createdby as depttest_createdby, depttest.createddate as depttest_createddate, "
			+ "depttest.lastmodifiedby as depttest_lastmodifiedby, depttest.lastmodifieddate as depttest_lastmodifieddate, docs.uuid as docs_uuid, "
			+ "docs.documentid as docs_documentid, docs.documentname as docs_documentname, docs.referencetype as docs_referencetype, "
			+ "docs.referenceid as docs_referenceid, docs.tenantid as docs_tenantid, docs.createdby as docs_createdby, docs.createddate as docs_createddate, "
			+ "docs.lastmodifiedby as docs_lastmodifiedby, docs.lastmodifieddate as docs_lastmodifieddate, jurisdiction.uuid as jurisdiction_uuid, "
			+ "jurisdiction.hierarchy as jurisdiction_hierarchy, jurisdiction.boundarytype as jurisdiction_boundarytype, jurisdiction.boundary as jurisdiction_boundary, "
			+ "jurisdiction.isactive as jurisdiction_isactive, jurisdiction.tenantid as jurisdiction_tenantid, jurisdiction.createdby as jurisdiction_createdby, jurisdiction.createddate as jurisdiction_createddate, "
			+ "jurisdiction.lastmodifiedby as jurisdiction_lastmodifiedby, jurisdiction.lastmodifieddate as jurisdiction_lastmodifieddate, history.uuid as history_uuid, "
			+ "history.servicestatus as history_servicestatus, history.servicefrom as history_servicefrom, history.serviceto as history_serviceto, "
			+ "history.ordernumber as history_ordernumber, history.iscurrentposition as history_iscurrentposition, history.location as history_location, "
			+ "history.tenantid as history_tenantid, history.createdby as history_createdby, history.createddate as history_createddate, "
			+ "history.lastmodifiedby as history_lastmodifiedby, history.lastmodifieddate as history_lastmodifieddate, deact.uuid as deact_uuid, "
			+ "deact.reasonfordeactivation as deact_reasonfordeactivation, deact.effectivefrom as deact_effectivefrom, deact.ordernumber as deact_ordernumber, "
			+ "deact.remarks as deact_remarks, deact.tenantid as deact_tenantid, deact.createdby as deact_createdby, "
			+ "deact.createddate as deact_createddate, deact.lastmodifiedby as deact_lastmodifiedby, deact.lastmodifieddate as deact_lastmodifieddate, "
			+ "react.uuid as react_uuid, react.reasonforreactivation as react_reasonforreactivation, react.effectivefrom as react_effectivefrom, react.ordernumber as react_ordernumber, "
			+ "react.remarks as react_remarks, react.tenantid as react_tenantid, react.createdby as react_createdby, "
			+ "react.createddate as react_createddate, react.lastmodifiedby as react_lastmodifiedby, react.lastmodifieddate as react_lastmodifieddate "
			+ "FROM eg_hrms_employee employee LEFT JOIN eg_hrms_assignment assignment ON employee.uuid = assignment.employeeid LEFT JOIN eg_hrms_educationaldetails education "
			+ "ON employee.uuid = education.employeeid LEFT JOIN eg_hrms_departmentaltests depttest ON employee.uuid = depttest.employeeid LEFT JOIN eg_hrms_empdocuments docs "
			+ "ON employee.uuid = docs.employeeid LEFT JOIN eg_hrms_servicehistory history ON employee.uuid = history.employeeid LEFT JOIN eg_hrms_jurisdiction jurisdiction "
			+ "ON employee.uuid = jurisdiction.employeeid LEFT JOIN eg_hrms_deactivationdetails deact ON employee.uuid = deact.employeeid LEFT JOIN eg_hrms_reactivationdetails react "
			+ "ON employee.uuid = react.employeeid WHERE ";

	public static final String HRMS_EMPLOYEE_TABLE_QUREY = "SELECT employee.id AS employee_id, employee.uuid AS employee_uuid, employee.code AS employee_code, " +
			"       employee.dateOfAppointment AS employee_doa, employee.employeestatus AS employee_status, " +
			"       employee.employeetype AS employee_type, employee.active AS employee_active, " +
			"       employee.reactivateemployee AS employee_reactive, employee.tenantid AS employee_tenantid, " +
			"       employee.createdby AS employee_createdby, employee.createddate AS employee_createddate, " +
			"       employee.lastmodifiedby AS employee_lastmodifiedby, employee.lastmodifieddate AS employee_lastmodifieddate " +
			" FROM eg_hrms_employee employee " +
			" WHERE ";

	public static final String SUBQUERY_EG_HRMS_ASSIGNMENT = "SELECT assignment.uuid AS assignment_uuid, assignment.employeeid, assignment.position, assignment.department, assignment.designation, " +
			"       assignment.fromdate, assignment.todate, assignment.govtordernumber, assignment.reportingto, " +
			"       assignment.ishod, assignment.iscurrentassignment, assignment.tenantid, assignment.createdby, " +
			"       assignment.createddate, assignment.lastmodifiedby, assignment.lastmodifieddate " +
			"FROM eg_hrms_assignment assignment " +
			"WHERE assignment.employeeid IN (:employeeIds); ";

	public static final String SUBQUERY_EG_HRMS_EDUCATIONALDETAILS = "SELECT education.uuid AS education_uuid, education.employeeid, education.qualification, education.stream, education.yearofpassing, " +
			"       education.university, education.remarks, education.isactive, education.tenantid, education.createdby, " +
			"       education.createddate, education.lastmodifiedby, education.lastmodifieddate " +
			"FROM eg_hrms_educationaldetails education " +
			"WHERE education.employeeid IN (:employeeIds); ";

	public static final String SUBQUERY_EG_HRMS_DEPARTMENTALTESTS = "SELECT depttest.uuid AS depttest_uuid, depttest.employeeid,  depttest.test, depttest.yearofpassing, depttest.remarks, " +
			"       depttest.isactive, depttest.tenantid, depttest.createdby, depttest.createddate, " +
			"       depttest.lastmodifiedby, depttest.lastmodifieddate " +
			"FROM eg_hrms_departmentaltests depttest " +
			"WHERE depttest.employeeid IN (:employeeIds); ";

	public static final String SUBQUERY_EG_HRMS_EMPDOCUMENTS = "SELECT docs.uuid AS docs_uuid, docs.employeeid, docs.documentid, docs.documentname, docs.referencetype, docs.referenceid, " +
			"       docs.tenantid, docs.createdby, docs.createddate, docs.lastmodifiedby, docs.lastmodifieddate " +
			"FROM eg_hrms_empdocuments docs " +
			"WHERE docs.employeeid IN (:employeeIds); ";

	public static final String SUBQUERY_EG_HRMS_SERVICEHISTORY = "SELECT history.uuid AS history_uuid, history.employeeid, history.servicestatus, history.servicefrom, history.serviceto, " +
			"       history.ordernumber, history.iscurrentposition, history.location, history.tenantid, " +
			"       history.createdby, history.createddate, history.lastmodifiedby, history.lastmodifieddate " +
			"FROM eg_hrms_servicehistory history " +
			"WHERE history.employeeid IN (:employeeIds); ";
	public static final String SUBQUERY_EG_HRMS_JURISDICTION = "SELECT jurisdiction.uuid, jurisdiction.employeeid, jurisdiction.hierarchy, jurisdiction.boundarytype, " +
			"       jurisdiction.boundary, jurisdiction.isactive, jurisdiction.tenantid, jurisdiction.createdby, " +
			"       jurisdiction.createddate, jurisdiction.lastmodifiedby, jurisdiction.lastmodifieddate " +
			"FROM eg_hrms_jurisdiction jurisdiction " +
			"WHERE jurisdiction.employeeid IN (:employeeIds) ";

	public static final String SUBQUERY_EG_HRMS_DEACTIVATIONDETAILS = "SELECT deact.uuid AS deact_uuid, deact.employeeid, deact.reasonfordeactivation, deact.effectivefrom, deact.ordernumber, " +
			"       deact.remarks, deact.tenantid, deact.createdby, deact.createddate, deact.lastmodifiedby, " +
			"       deact.lastmodifieddate " +
			"FROM eg_hrms_deactivationdetails deact " +
			"WHERE deact.employeeid IN (:employeeIds); ";

	public static final String SUBQUERY_EG_HRMS_REACTIVATIONDETAILS = "SELECT react.uuid AS react_uuid, react.employeeid, react.reasonforreactivation, react.effectivefrom, react.ordernumber, " +
			"       react.remarks, react.tenantid, react.createdby, react.createddate, react.lastmodifiedby, " +
			"       react.lastmodifieddate " +
			"FROM eg_hrms_reactivationdetails react " +
			"WHERE react.employeeid IN (:employeeIds); ";

	public static final String HRMS_PAGINATION_WRAPPER = "SELECT * FROM "
			+ "(SELECT *, DENSE_RANK() OVER (ORDER BY employee_uuid) offset_ FROM " + "({})" + " result) result_offset "
			+ "WHERE offset_ > $offset AND offset_ <= $limit";

	public static final String HRMS_PAGINATION_ADDENDUM = " ORDER BY employee.lastmodifieddate DESC LIMIT $limit OFFSET $offset";
	
	public static final String HRMS_POSITION_SEQ = "SELECT NEXTVAL('EG_HRMS_POSITION')";

	public static final String HRMS_GET_ASSIGNMENT = "select distinct(employeeid)  from eg_hrms_assignment assignment where assignment.tenantid notnull  ";

	public static final String HRMS_COUNT_EMP_QUERY = "SELECT active, count(*) FROM eg_hrms_employee WHERE tenantid ";

	public static final String HRMS_GET_UNASSIGNED_EMPLOYEES = "SELECT employee.uuid from eg_hrms_employee employee LEFT JOIN eg_hrms_assignment assignment ON employee.uuid = assignment.employeeid where assignment.employeeid is null";
}
