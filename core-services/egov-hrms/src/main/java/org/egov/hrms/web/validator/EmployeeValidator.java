package org.egov.hrms.web.validator;

import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.hrms.config.PropertiesManager;
import org.egov.hrms.model.Assignment;
import org.egov.hrms.model.DeactivationDetails;
import org.egov.hrms.model.DepartmentalTest;
import org.egov.hrms.model.EducationalQualification;
import org.egov.hrms.model.Employee;
import org.egov.hrms.model.Jurisdiction;
import org.egov.hrms.model.ReactivationDetails;
import org.egov.hrms.model.ServiceHistory;
import org.egov.hrms.repository.RestCallRepository;
import org.egov.hrms.service.EmployeeService;
import org.egov.hrms.service.MDMSService;
import org.egov.hrms.service.UserService;
import org.egov.hrms.utils.ErrorConstants;
import org.egov.hrms.utils.HRMSConstants;
import org.egov.hrms.utils.HRMSUtils;
import org.egov.hrms.web.contract.EmployeeRequest;
import org.egov.hrms.web.contract.EmployeeResponse;
import org.egov.hrms.web.contract.EmployeeSearchCriteria;
import org.egov.hrms.web.contract.UserResponse;
import org.egov.hrms.web.models.boundary.BoundaryResponse;
import org.egov.mdms.model.MdmsResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import static org.egov.hrms.utils.ErrorConstants.CITIZEN_TYPE_CODE;

@Service
@Slf4j
public class EmployeeValidator {
	
	@Autowired
	private MDMSService mdmsService;

	@Autowired
	private EmployeeService employeeService;

	@Autowired
	private UserService userService;
	
	@Autowired
	private PropertiesManager propertiesManager;

	@Autowired
	private HRMSUtils hrmsUtils;

	@Autowired
	private RestCallRepository restCallRepository;

	/**
	 * Validates employee request for create. Validations include:
	 * 1. Validating MDMS codes
	 * 2. Performing data sanity checks
	 * 
	 * @param request
	 */
	public void validateCreateEmployee(EmployeeRequest request) {
		validateCreateEmployee(request, new ArrayList<>());
	}

	/**
	 * Validates the create request. Row-level failures (per-employee username
	 * duplicates) are appended to {@code rowFailuresOut} and the corresponding
	 * employees are REMOVED from {@code request.getEmployees()} so the caller
	 * can proceed with the surviving subset — enabling partial-success on bulk
	 * creates. Batch-fatal issues (invalid MDMS codes, missing passwords,
	 * in-batch duplicate codes) still {@code throw CustomException} so a
	 * malformed request never gets partially processed.
	 *
	 * @param request         the create request; its employees list is mutated
	 *                        by removing rows that failed row-level checks.
	 * @param rowFailuresOut  populated with one entry per row that was filtered
	 *                        out. Each entry contains at least {@code code},
	 *                        {@code errorCode}, {@code errorMessage}, and
	 *                        {@code source}.
	 */
	public void validateCreateEmployee(EmployeeRequest request,
			List<Map<String, Object>> rowFailuresOut) {
		Map<String, String> errorMap = new HashMap<>();
		validateDataUniqueness(request.getEmployees(), errorMap);
		autoGenerateMissingMobileNumbers(request.getEmployees(), request.getRequestInfo());
		// Row-level: per-employee failures don't populate errorMap; they mark
		// the offender for exclusion and let the batch proceed.
		filterExistingUsernames(request.getEmployees(), request.getRequestInfo(), rowFailuresOut);
		validatePassword(request, errorMap);
		if(!CollectionUtils.isEmpty(errorMap.keySet()))
			throw new CustomException(errorMap);
		if (request.getEmployees().isEmpty()) {
			// All rows failed row-level checks. Nothing to validate against MDMS.
			// Caller (controller) will return a response containing just the failures.
			return;
		}
		Map<String, List<String>> boundaryMap = getBoundaryList(request.getRequestInfo(),request.getEmployees().get(0));
		//FIXME hierarchy type has to be validated
		Map<String, List<String>> mdmsData = mdmsService.getMDMSData(request.getRequestInfo(), request.getEmployees().get(0).getTenantId());
		if(!CollectionUtils.isEmpty(mdmsData.keySet())){
			request.getEmployees().stream().forEach(employee -> validateMdmsData(employee, errorMap, mdmsData,boundaryMap));
		}
		if(!CollectionUtils.isEmpty(errorMap.keySet()))
			throw new CustomException(errorMap);
	}

	private void validatePassword(EmployeeRequest request, Map<String, String> errorMap) {
		List<Employee> employees = request.getEmployees();
		if (!propertiesManager.isAutoGeneratePassword()) {
			employees.forEach(employee -> {
				if (StringUtils.isEmpty(employee.getUser().getPassword()))
					errorMap.put(ErrorConstants.HRMS_PASSWORD_REQUIRED, ErrorConstants.HRMS_PASSWORD_REQUIRED_MSG);
			});
		}
	}

	public Map<String, List<String>> getBoundaryList(RequestInfo requestInfo,Employee employee){
		List<String> boundarytList = new ArrayList<>();
		Map<String, List<String>> eachMasterMap = new HashMap<>();
		Map<String, List<String>> masterData = new HashMap<>();
		if(!CollectionUtils.isEmpty(employee.getJurisdictions())){
			for(Jurisdiction jurisdiction: employee.getJurisdictions()){
				if(!boundarytList.contains(jurisdiction.getBoundary()))
					boundarytList.add(jurisdiction.getBoundary());
			}
			if(CollectionUtils.isEmpty(boundarytList))
				boundarytList.add(employee.getTenantId());
		}

		List<MdmsResponse> boundaryResponseList = new ArrayList<>();
//		for(String boundary: boundarytList){
//			MdmsResponse responseLoc = mdmsService.fetchMDMSDataLoc(requestInfo, boundary);
//			BoundaryResponse boundarySearchResponse = serviceRequestClient.fetchResult(
//					new StringBuilder(propertiesManager.getBoundaryServiceHost()
//							+ propertiesManager.getBoundarySearchUrl()
//							+"?limit=" + boundaries.size()
//							+ "&offset=0&tenantId=" + tenantId
//							+ "&codes=" + String.join(",", boundaries)),
//					request.getRequestInfo(),
//					BoundaryResponse.class
//			);
//			if(!CollectionUtils.isEmpty(responseLoc.getMdmsRes()))
//				boundaryResponseList.add(responseLoc);
//		}

		if(!CollectionUtils.isEmpty(boundarytList)) {
			try {
				BoundaryResponse boundarySearchResponse = restCallRepository.fetchResult(
						new StringBuilder(propertiesManager.getBoundaryServiceHost()
								+ propertiesManager.getBoundarySearchUrl()
								+"?limit=" + boundarytList.size()
								+ "&offset=0&tenantId=" + employee.getTenantId()
								+ "&codes=" + String.join(",", boundarytList)),
						requestInfo,
						BoundaryResponse.class
				);
				masterData.put(HRMSConstants.HRMS_MDMS_TENANT_BOUNDARY_CODE, boundarySearchResponse.getBoundary().stream()
						.map(boundary -> boundary.getCode())
						.collect(Collectors.toList())
				);
				log.info("successfully fetch boundary");
			} catch (Exception e) {
				log.error("error while fetching boundary");
				log.error("Error while fetching boundaries from Boundary Service", e);
				throw new CustomException("BOUNDARY_SERVICE_SEARCH_ERROR","Error while fetching boundaries from Boundary Service : " + e.getMessage());
			}
		}

		return masterData;
	}
	
	/**
	 * Validates search request. Checks the following:
	 * 1. If a user who doesn't have access to open search is making an open search call.
	 * 
	 * @param requestInfo
	 * @param criteria
	 */
	public void validateSearchRequest(RequestInfo requestInfo, EmployeeSearchCriteria criteria) {
		Map<String, String> errorMap = new HashMap<>();

		if(requestInfo.getUserInfo() != null && requestInfo.getUserInfo().getType().equalsIgnoreCase(CITIZEN_TYPE_CODE) && !CollectionUtils.isEmpty(criteria.getIds()))
			errorMap.put(ErrorConstants.HRMS_INVALID_SEARCH_CITIZEN_CODE, ErrorConstants.HRMS_INVALID_SEARCH_CITIZEN_MSG);

		if(criteria.isCriteriaEmpty(criteria)) {
			String[] roles = propertiesManager.getOpenSearchEnabledRoles().split(",");
			List<String> reqroles = requestInfo.getUserInfo().getRoles().stream().map(Role::getCode).collect(Collectors.toList());
			boolean check = false;
			for(String role : reqroles) {
				if(Arrays.asList(roles).contains(role)) {
					check = true;
					break;
				}
			}
			if(!check) {
				errorMap.put(ErrorConstants.HRMS_INVALID_SEARCH_REQ_CODE, ErrorConstants.HRMS_INVALID_SEARCH_REQ_MSG);
			}
		}
		if(null != criteria.getAsOnDate()) {
			if(CollectionUtils.isEmpty(criteria.getDepartments()) || CollectionUtils.isEmpty(criteria.getDesignations()))
				errorMap.put(ErrorConstants.HRMS_INVALID_SEARCH_AOD_CODE, ErrorConstants.HRMS_INVALID_SEARCH_AOD_MSG);
		}

        if(!CollectionUtils.isEmpty( criteria.getRoles()) && StringUtils.isEmpty(criteria.getTenantId())) {
            errorMap.put(ErrorConstants.HRMS_INVALID_SEARCH_ROLES_CODE, ErrorConstants.HRMS_INVALID_SEARCH_ROLES_MSG);
        }

        if((!StringUtils.isEmpty(criteria.getPhone()) || !CollectionUtils.isEmpty(criteria.getNames())) &&
				StringUtils.isEmpty(criteria.getTenantId())) {
			errorMap.put(ErrorConstants.HRMS_INVALID_SEARCH_USER_CODE, ErrorConstants.HRMS_INVALID_SEARCH_USER_MSG);
		}
		if(!CollectionUtils.isEmpty(errorMap.keySet()))
			throw new CustomException(errorMap);
	}

	/**
	 * Checks if the employee being created is duplicate with the following:
	 * 1. Validating mobile number
	 * 2. Validating username
	 * 
	 * @param request
	 * @param errorMap
	 */
	// Kept for backward compatibility with any external caller that still
	// invokes the old signature. New code should use
	// {@link #validateCreateEmployee(EmployeeRequest, List)} which supports
	// partial-success semantics.
	private void validateExistingDuplicates(EmployeeRequest request, Map<String, String> errorMap) {
		List<Employee> employees = request.getEmployees();
		validateDataUniqueness(employees,errorMap);
        autoGenerateMissingMobileNumbers(employees, request.getRequestInfo());
        validateExistingUsernames(employees, errorMap, request.getRequestInfo());
	}

	/**
	 * Checks duplicate occurance of mobileNumber and code for bulk request
	 *
	 * @param employees
	 * @param errorMap
	 */
	private void validateDataUniqueness(List<Employee> employees, Map<String, String> errorMap) {
		HashSet < String> codes = new HashSet<>();
		employees.forEach(employee -> {
			if(null != employee.getCode()){
				if (codes.contains(employee.getCode()))
					errorMap.put(ErrorConstants.HRMS_BULK_CREATE_DUPLICATE_EMPCODE_CODE,ErrorConstants.HRMS_BULK_CREATE_DUPLICATE_EMPCODE_MSG);
				else
					codes.add(employee.getCode());
			}
		});
	}

	/**
	 * Auto-generates a mobile number for any employee that doesn't already have one.
	 *
	 * Mobile-number duplicates are now permitted (only usernames must be unique), so the generated
	 * number is no longer re-queried against the user service for uniqueness, and duplicate mobile
	 * numbers within the request are no longer flagged as errors.
	 *
	 * @param employees
	 * @param requestInfo
	 */
    private void autoGenerateMissingMobileNumbers(List<Employee> employees, RequestInfo requestInfo) {
        employees.forEach(employee -> {
			boolean autoGenerateMobileNumber = employee.getUser().getMobileNumber() == null ||
					employee.getUser().getMobileNumber().isEmpty();
			if (autoGenerateMobileNumber) {
				employee.getUser().setMobileNumber(
						hrmsUtils.generateMobileNumber(requestInfo, employee.getTenantId()));
			}
        });
    }

    /**
     * Checks whether any employee's username (employee code) already exists as an EMPLOYEE user.
     *
     * Uses a single bulk search per tenant ({@link UserService#searchByUsernames}) instead of the
     * earlier per-employee search loop. Duplicate rule: a username match is a duplicate (whether or
     * not the mobile number also matches); a mobile-number-only match is not an issue, so mobile
     * numbers are not searched.
     *
     * @param employees
     * @param errorMap
     * @param requestInfo
     */
    /**
     * Partial-success variant of {@link #validateExistingUsernames} that
     * REMOVES any employee whose code already exists in the user backend from
     * the passed-in list and records one entry per removed row in
     * {@code rowFailuresOut}. The remaining employees proceed with create.
     *
     * The failure entry records WHICH source detected the duplicate — either
     * "individual" (the primary search hit) or "egov-user" (the cross-check
     * hit that catches legacy users with no matching individual row). Callers
     * see the exact backend that owns the conflict.
     */
    private void filterExistingUsernames(List<Employee> employees,
            RequestInfo requestInfo, List<Map<String, Object>> rowFailuresOut) {
        // Group the usernames (employee codes) to look up, per tenant
        Map<String, Set<String>> usernamesByTenant = new HashMap<>();
        employees.forEach(employee -> {
            if (!StringUtils.isEmpty(employee.getCode())) {
                usernamesByTenant
                        .computeIfAbsent(employee.getTenantId(), k -> new LinkedHashSet<>())
                        .add(employee.getCode());
            }
        });
        if (usernamesByTenant.isEmpty()) return;

        // Ask userService.searchByUsernames — that implementation queries
        // Individual and cross-checks egov-user, returning hits from either
        // AND attributing each hit to its source in sourceByUsername.
        Set<String> existingUsernames = new HashSet<>();
        Map<String, String> sourceByUsername = new HashMap<>();
        usernamesByTenant.forEach((tenantId, usernames) -> {
            UserResponse resp = userService.searchByUsernames(requestInfo, new ArrayList<>(usernames), tenantId);
            if (resp != null && !CollectionUtils.isEmpty(resp.getUser())) {
                resp.getUser().forEach(u -> {
                    if (!StringUtils.isEmpty(u.getUserName())) existingUsernames.add(u.getUserName());
                });
            }
            if (resp != null && resp.getSourceByUsername() != null) {
                sourceByUsername.putAll(resp.getSourceByUsername());
            }
        });
        if (existingUsernames.isEmpty()) return;

        // Remove duplicates from the batch and record a failure entry for each.
        Iterator<Employee> it = employees.iterator();
        while (it.hasNext()) {
            Employee e = it.next();
            if (StringUtils.isEmpty(e.getCode())) continue;
            if (existingUsernames.contains(e.getCode())) {
                String src = sourceByUsername.getOrDefault(e.getCode(), "unknown");
                String storeLabel = "individual".equals(src) ? "the individual data store (os.individual)"
                        : "egov-user".equals(src) ? "the egov-user data store (eg_user)"
                        : "the individual/egov-user store";
                Map<String, Object> row = new HashMap<>();
                row.put("code", e.getCode());
                row.put("userName", e.getUser() != null ? e.getUser().getUserName() : null);
                row.put("mobileNumber", e.getUser() != null ? e.getUser().getMobileNumber() : null);
                row.put("errorCode", "HRMS_EMPLOYEE_CREATE_USERNAME_ALREADY_EXISTS");
                row.put("errorMessage", String.format(
                        "Employee code '%s' already exists as a username in %s. "
                                + "This row was skipped. Fresh rows in the batch (if any) were created.",
                        e.getCode(), storeLabel));
                row.put("source", src);
                rowFailuresOut.add(row);
                it.remove();
            }
        }
    }

    private void validateExistingUsernames(List<Employee> employees, Map<String, String> errorMap, RequestInfo requestInfo) {
        // Group the usernames (employee codes) to look up, per tenant
        Map<String, Set<String>> usernamesByTenant = new HashMap<>();
        employees.forEach(employee -> {
            if (!StringUtils.isEmpty(employee.getCode())) {
                usernamesByTenant
                        .computeIfAbsent(employee.getTenantId(), k -> new LinkedHashSet<>())
                        .add(employee.getCode());
            }
        });
        if (usernamesByTenant.isEmpty())
            return;

        // Collect the usernames that already exist in the user/individual service
        Set<String> existingUsernames = new HashSet<>();
        usernamesByTenant.forEach((tenantId, usernames) -> {
            UserResponse userResponse = userService.searchByUsernames(requestInfo, new ArrayList<>(usernames), tenantId);
            if (userResponse != null && !CollectionUtils.isEmpty(userResponse.getUser())) {
                userResponse.getUser().forEach(user -> {
                    if (!StringUtils.isEmpty(user.getUserName()))
                        existingUsernames.add(user.getUserName());
                });
            }
        });

        // Flag duplicates with the actual usernames that collided (client
        // must know WHICH employee code is the offender).
        List<String> duplicateUsernames = employees.stream()
                .map(Employee::getCode)
                .filter(code -> !StringUtils.isEmpty(code) && existingUsernames.contains(code))
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (!duplicateUsernames.isEmpty()) {
            errorMap.put("HRMS_EMPLOYEE_CREATE_USERNAME_ALREADY_EXISTS",
                    String.format("The following employee code(s) already exist as usernames in the user service: [%s]. "
                                    + "Choose different codes or update the existing employees.",
                            String.join(", ", duplicateUsernames)));
        }
    }

    /**
     * Validates MDMS codes of the request.
     * 
     * @param employee
     * @param errorMap
     * @param mdmsData
     */
	private void validateMdmsData(Employee employee, Map<String, String> errorMap, Map<String, List<String>> mdmsData, Map<String, List<String>> boundaryMap) {
		validateEmployee(employee, errorMap, mdmsData);
		validateAssignments(employee, errorMap, mdmsData);
		validateServiceHistory(employee, errorMap, mdmsData);
//		validateJurisdicton(employee, errorMap, mdmsData, boundaryMap);
		validateEducationalDetails(employee, errorMap, mdmsData);
		validateDepartmentalTest(employee, errorMap, mdmsData);
	}


	/**
	 * Performs checks for maintaining data consistency
	 *  @param employee
	 * @param errorMap
	 * @param mdmsData
	 * @param existingEmp
	 * @param requestInfo
	 */
	public void validateDataConsistency(Employee employee, Map<String, String> errorMap, Map<String, List<String>> mdmsData, Employee existingEmp, RequestInfo requestInfo) {
		validateUserData(existingEmp,employee,errorMap, requestInfo);
		validateConsistencyAssignment(existingEmp,employee,errorMap);
		validateConsistencyJurisdiction(existingEmp,employee,errorMap);
		validateConsistencyDepartmentalTest(existingEmp,employee,errorMap);
		validateConsistencyEducationalDetails(existingEmp,employee,errorMap);
		validateConsistencyServiceHistory(existingEmp, employee, errorMap);
		validateConsistencyEmployeeDocument(existingEmp, employee, errorMap);
		validateConsistencyDeactivationDetails(existingEmp, employee, errorMap);
		if(!employee.getIsActive())
			validateDeactivationDetails(existingEmp, employee, errorMap, mdmsData);
		if(employee.getIsActive() && employee.getReActivateEmployee())
			validateReactivationDetails(existingEmp, employee, errorMap, mdmsData);
	}

	/**
	 * Check whether employee code has changed
	 * @param existingEmp
	 * @param employee
	 * @param errorMap
	 * @param requestInfo
	 */
	private void validateUserData(Employee existingEmp, Employee employee, Map<String, String> errorMap, RequestInfo requestInfo) {
		if(!employee.getCode().equals(existingEmp.getCode()))
			errorMap.put(ErrorConstants.HRMS_UPDATE_EMPLOYEE_CODE_CHANGE_CODE,ErrorConstants.HRMS_UPDATE_EMPLOYEE_CODE_CHANGE_MSG);
		// Mobile-number duplicates are now permitted (only usernames must be unique), so the previous
		// "mobile number already used by another user" check on update has been removed.
	}

	/**
	 * Checks the following:
	 * 1. Whether the mobile number is valid
	 * 2. Whether the roles are valid
	 * 3. Whether the employee status mentioned is valid.
	 * 4. Whether the employee type mentioned is valid
	 * 5. Whether the date of appointment of the employee is valid.
	 * 
	 * @param employee
	 * @param errorMap
	 * @param mdmsData
	 */
	private void validateEmployee(Employee employee, Map<String, String> errorMap, Map<String, List<String>> mdmsData) {

		String empCode = employee.getCode() != null ? employee.getCode() : "<no-code>";

		if(employee.getUser().getMobileNumber().length() < 8 || employee.getUser().getMobileNumber().length() > 11) {
			errorMap.put(ErrorConstants.HRMS_INVALID_MOB_NO_CODE, ErrorConstants.HRMS_INVALID_MOB_NO_MSG);
		}

		if (CollectionUtils.isEmpty(employee.getUser().getRoles())) {
			errorMap.put("HRMS_EMPLOYEE_CREATE_MISSING_ROLES:" + empCode,
					String.format("Employee code='%s' has no roles. At least one role is required.", empCode));
		} else {
			List<String> validRoles = mdmsData.get(HRMSConstants.HRMS_MDMS_ROLES_CODE);
			for (org.egov.hrms.model.Role role : employee.getUser().getRoles()) {
				if (!validRoles.contains(role.getCode())) {
					errorMap.put("HRMS_EMPLOYEE_CREATE_INVALID_ROLE:" + empCode + ":" + role.getCode(),
							String.format("Employee code='%s' has invalid role code='%s'. "
											+ "Not present in MDMS ACCESSCONTROL-ROLES. Sample valid codes: %s",
									empCode, role.getCode(),
									validRoles.stream().limit(5).collect(Collectors.joining(", "))));
				}
			}
		}
		List<String> validTypes = mdmsData.get(HRMSConstants.HRMS_MDMS_EMP_TYPE_CODE);
		if (!validTypes.contains(employee.getEmployeeType())) {
			errorMap.put("HRMS_EMPLOYEE_CREATE_INVALID_EMPLOYEE_TYPE:" + empCode,
					String.format("Employee code='%s' has invalid employeeType='%s'. "
									+ "Valid values: [%s]",
							empCode, employee.getEmployeeType(),
							String.join(", ", validTypes)));
		}
		if (null != employee.getDateOfAppointment() && employee.getDateOfAppointment() > new Date().getTime())
			errorMap.put("HRMS_EMPLOYEE_CREATE_INVALID_DATE_OF_APPOINTMENT:" + empCode,
					String.format("Employee code='%s' has dateOfAppointment=%d in the future.",
							empCode, employee.getDateOfAppointment()));
		if (null != employee.getUser().getDob()) {
			if (employee.getUser().getDob() >= new Date().getTime())
				errorMap.put("HRMS_EMPLOYEE_CREATE_INVALID_DOB:" + empCode,
						String.format("Employee code='%s' has user.dob=%d in the future.",
								empCode, employee.getUser().getDob()));
			if (null != employee.getDateOfAppointment() && employee.getDateOfAppointment() < employee.getUser().getDob())
				errorMap.put("HRMS_EMPLOYEE_CREATE_APPOINTMENT_BEFORE_DOB:" + empCode,
						String.format("Employee code='%s' has dateOfAppointment=%d earlier than user.dob=%d.",
								empCode, employee.getDateOfAppointment(), employee.getUser().getDob()));
		}
	}
	
	/**
	 * Checks the following:
	 * 1. If there is more than one current assignment.
	 * 2. if period of assignment of any of the assignments overlap with that of others.
	 * 3. if the Department code is valid
	 * 4. If the Designation code is valid
	 * 5. If the assignment dates are valid
	 * 
	 * @param employee
	 * @param errorMap
	 * @param mdmsData
	 */
	private void validateAssignments(Employee employee, Map<String, String> errorMap, Map<String, List<String>> mdmsData) {
		if (employee.getAssignments() != null && !employee.getAssignments().isEmpty()) {
			List<Assignment> currentAssignments = employee.getAssignments().stream().filter(assignment -> assignment.getIsCurrentAssignment()).collect(Collectors.toList());
			if (currentAssignments.size() != 1) {
				errorMap.put(ErrorConstants.HRMS_INVALID_CURRENT_ASSGN_CODE, ErrorConstants.HRMS_INVALID_CURRENT_ASSGN_MSG);
			}
			employee.getAssignments().sort(new Comparator<Assignment>() {
				@Override
				public int compare(Assignment assignment1, Assignment assignment2) {
					return assignment1.getFromDate().compareTo(assignment2.getFromDate());
				}
			});
			int length = employee.getAssignments().size();
			boolean overlappingCheck = false;
			for (int i = 0; i < length - 1; i++) {
				if (null != employee.getAssignments().get(i).getToDate() && employee.getAssignments().get(i).getToDate() > employee.getAssignments().get(i + 1).getFromDate())
					overlappingCheck = true;
			}
			if (overlappingCheck)
				errorMap.put(ErrorConstants.HRMS_OVERLAPPING_ASSGN_CODE, ErrorConstants.HRMS_OVERLAPPING_ASSGN_MSG);

			String empCode = employee.getCode() != null ? employee.getCode() : "<no-code>";
			List<String> validDepts = mdmsData.get(HRMSConstants.HRMS_MDMS_DEPT_CODE);
			for (Assignment assignment : employee.getAssignments()) {
				if (!assignment.getIsCurrentAssignment() && !CollectionUtils.isEmpty(currentAssignments) && null != assignment.getToDate() && currentAssignments.get(0).getFromDate() < assignment.getToDate())
					errorMap.put(ErrorConstants.HRMS_OVERLAPPING_ASSGN_CURRENT_CODE, ErrorConstants.HRMS_OVERLAPPING_ASSGN_CURRENT_MSG);
				if (!validDepts.contains(assignment.getDepartment()))
					errorMap.put("HRMS_EMPLOYEE_CREATE_INVALID_DEPARTMENT:" + empCode + ":" + assignment.getDepartment(),
							String.format("Employee code='%s' has invalid assignment department='%s'. "
											+ "Not present in MDMS common-masters.Department. Valid codes: [%s]",
									empCode, assignment.getDepartment(),
									String.join(", ", validDepts)));
				/*if (!assignment.getDesignation().equalsIgnoreCase("undefined") &&
						!mdmsData.get(HRMSConstants.HRMS_MDMS_DESG_CODE).contains(assignment.getDesignation()))
					errorMap.put(ErrorConstants.HRMS_INVALID_DESG_CODE, ErrorConstants.HRMS_INVALID_DESG_MSG);*/
				if (assignment.getIsCurrentAssignment() && null != assignment.getToDate())
					errorMap.put(ErrorConstants.HRMS_INVALID_ASSIGNMENT_CURRENT_TO_DATE_CODE, ErrorConstants.HRMS_INVALID_ASSIGNMENT_CURRENT_TO_DATE_MSG);
				if (!assignment.getIsCurrentAssignment() && null == assignment.getToDate())
					errorMap.put(ErrorConstants.HRMS_INVALID_ASSIGNMENT_NON_CURRENT_TO_DATE_CODE, ErrorConstants.HRMS_INVALID_ASSIGNMENT_NON_CURRENT_TO_DATE_MSG);
				if (null != assignment.getToDate() && assignment.getFromDate() > assignment.getToDate())
					errorMap.put(ErrorConstants.HRMS_INVALID_ASSIGNMENT_PERIOD_CODE, ErrorConstants.HRMS_INVALID_ASSIGNMENT_PERIOD_MSG);
				if (employee.getUser().getDob() != null)
					if (assignment.getFromDate() < employee.getUser().getDob() || (null != assignment.getToDate() && assignment.getToDate() < employee.getUser().getDob()))
						errorMap.put(ErrorConstants.HRMS_INVALID_ASSIGNMENT_DATES_CODE, ErrorConstants.HRMS_INVALID_ASSIGNMENT_DATES_MSG);
				if (null != employee.getDateOfAppointment() && assignment.getFromDate() < employee.getDateOfAppointment())
					errorMap.put(ErrorConstants.HRMS_INVALID_ASSIGNMENT_DATES_APPOINTMENT_CODE, ErrorConstants.HRMS_INVALID_ASSIGNMENT_DATES_APPOINTMENT_MSG);

			}
		}
		
	}

	/**
	 * Checks the follwing:
	 * 1. If the status of service is valid.
	 * 2. If the service period is valid.
	 * 3. If the service dates is valid.
	 * 4. If there is more than 1 current Positions.
	 * 5. If service end date is null for current position
	 * 
	 * @param employee
	 * @param errorMap
	 * @param mdmsData
	 */
	private void validateServiceHistory(Employee employee, Map<String, String> errorMap, Map<String, List<String>> mdmsData) {
		if(!CollectionUtils.isEmpty(employee.getServiceHistory())){
			List<ServiceHistory> currentService = employee.getServiceHistory().stream().filter(serviceHistory -> null!= serviceHistory.getIsCurrentPosition() && serviceHistory.getIsCurrentPosition()).collect(Collectors.toList());
			if(currentService.size() > 1){
				errorMap.put(ErrorConstants.HRMS_INVALID_CURRENT_SERVICE_CODE, ErrorConstants.HRMS_INVALID_CURRENT_SERVICE_MSG);
			}
			for(ServiceHistory history: employee.getServiceHistory()) {
				if( (null== history.getIsCurrentPosition() || !history.getIsCurrentPosition()) && !CollectionUtils.isEmpty(currentService) && null != currentService.get(0).getServiceFrom() && null != history.getServiceTo() && currentService.get(0).getServiceFrom()<history.getServiceTo() )
					errorMap.put(ErrorConstants.HRMS_OVERLAPPING_SERVICEHISTORY_CURRENT_CODE, ErrorConstants.HRMS_OVERLAPPING_SERVICEHISTORY_CURRENT_MSG);
				if( null!= history.getIsCurrentPosition() && history.getIsCurrentPosition() && null != history.getServiceTo())
					errorMap.put(ErrorConstants.HRMS_INVALID_SERVICE_CURRENT_TO_DATE_CODE,ErrorConstants.HRMS_INVALID_SERVICE_CURRENT_TO_DATE_MSG);
				if((null == history.getIsCurrentPosition() || !history.getIsCurrentPosition()) && null == history.getServiceTo())
					errorMap.put(ErrorConstants.HRMS_INVALID_SERVICE_NON_CURRENT_TO_DATE_CODE,ErrorConstants.HRMS_INVALID_SERVICE_NON_CURRENT_TO_DATE_MSG);
				if(!StringUtils.isEmpty(history.getServiceStatus()) && !mdmsData.get(HRMSConstants.HRMS_MDMS_EMP_STATUS_CODE).contains(history.getServiceStatus()))
					errorMap.put(ErrorConstants.HRMS_INVALID_SERVICE_STATUS_CODE, ErrorConstants.HRMS_INVALID_SERVICE_STATUS_MSG);
				if( (null != history.getServiceFrom() &&  history.getServiceFrom() > new Date().getTime()) || (null != history.getServiceTo() && history.getServiceTo() > new Date().getTime())
						|| (null != history.getServiceFrom() && null != history.getServiceTo() && history.getServiceFrom() > history.getServiceTo()))
					errorMap.put(ErrorConstants.HRMS_INVALID_SERVICE_PERIOD_CODE, ErrorConstants.HRMS_INVALID_SERVICE_PERIOD_MSG);
				if(employee.getUser().getDob()!=null )
					if((null != history.getServiceFrom() && history.getServiceFrom() < employee.getUser().getDob()) || (null != history.getServiceTo() && history.getServiceTo() < employee.getUser().getDob()))
						errorMap.put(ErrorConstants.HRMS_INVALID_SERVICE_DATES_CODE, ErrorConstants.HRMS_INVALID_SERVICE_DATES_MSG);
			}
		}
	}
	
	/**
	 * Checks the following:
	 * 1. If the qualification is valid.
	 * 2. If the specialization provided is valid.
	 * 3. If the year of passing is valid.
	 * 
	 * @param employee
	 * @param errorMap
	 * @param mdmsData
	 */
	private void validateEducationalDetails(Employee employee, Map<String, String> errorMap, Map<String, List<String>> mdmsData) {
		if(!CollectionUtils.isEmpty(employee.getEducation())){
			for(EducationalQualification education : employee.getEducation()) {
				if(null!= education.getQualification() && !mdmsData.get(HRMSConstants.HRMS_MDMS_QUALIFICATION_CODE).contains(education.getQualification()))
					errorMap.put(ErrorConstants.HRMS_INVALID_QUALIFICATION_CODE, ErrorConstants.HRMS_INVALID_QUALIFICATION_MSG);
				if(null != education.getStream() && !mdmsData.get(HRMSConstants.HRMS_MDMS_STREAMS_CODE).contains(education.getStream()))
					errorMap.put(ErrorConstants.HRMS_INVALID_EDUCATIONAL_STREAM_CODE, ErrorConstants.HRMS_INVALID_EDUCATIONAL_STREAM_MSG);
				if(null != education.getYearOfPassing() && education.getYearOfPassing() > new Date().getTime()){
					errorMap.put(ErrorConstants.HRMS_INVALID_EDUCATIONAL_PASSING_YEAR_CODE, ErrorConstants.HRMS_INVALID_EDUCATIONAL_PASSING_YEAR_MSG);
				}
			}
		}
	}

	/**
	 * 1. Checks if there is atleast 1 active jurisdiction
	 * 2. If hierarchy is valid
	 * 3. If boundaryType is valid
	 * 4. If boundary is valid
	 *
	 * @param employee
	 * @param errorMap
	 * @param mdmsData
	 */
	private void validateJurisdicton(Employee employee, Map<String, String> errorMap, Map<String, List<String>> mdmsData,Map<String, List<String>> boundaryMap) {
		if(CollectionUtils.isEmpty(employee.getJurisdictions().stream().filter(jurisdiction -> null == jurisdiction.getIsActive() || jurisdiction.getIsActive() &&  jurisdiction.getIsActive() ).collect(Collectors.toList()))){
			errorMap.put(ErrorConstants.HRMS_INVALID_JURISDICTION_ACTIIEV_NULL_CODE,ErrorConstants.HRMS_INVALID_JURISDICTION_ACTIIEV_NULL_MSG);
		}
		for(Jurisdiction jurisdiction: employee.getJurisdictions()) {
				String hierarchy_type_path = String.format(HRMSConstants.HRMS_TENANTBOUNDARY_HIERARCHY_JSONPATH,jurisdiction.getBoundary());
				String boundary_type_path = String.format(HRMSConstants.HRMS_TENANTBOUNDARY_BOUNDARY_TYPE_JSONPATH,jurisdiction.getHierarchy(),jurisdiction.getBoundary());
				String boundary_value_path = String.format(HRMSConstants.HRMS_TENANTBOUNDARY_BOUNDARY_VALUE_JSONPATH,jurisdiction.getHierarchy(),jurisdiction.getBoundary());
				List<String>  hierarchyTypes = JsonPath.read(boundaryMap,hierarchy_type_path);
				List <String> boundaryTypes = JsonPath.read(boundaryMap,boundary_type_path);
				List <String> boundaryValues = JsonPath.read(boundaryMap,boundary_value_path);
				if(!hierarchyTypes.contains(jurisdiction.getHierarchy()))
					errorMap.put(ErrorConstants.HRMS_INVALID_JURISDICTION_HEIRARCHY_CODE, ErrorConstants.HRMS_INVALID_JURISDICTION_HEIRARCHY_MSG);
				if(!boundaryTypes.contains(jurisdiction.getBoundaryType()))
					errorMap.put(ErrorConstants.HRMS_INVALID_JURISDICTION_BOUNDARY_TYPE_CODE, ErrorConstants.HRMS_INVALID_JURISDICTION_BOUNDARY_TYPE_MSG);
				if(!boundaryValues.contains(jurisdiction.getBoundary()))
					errorMap.put(ErrorConstants.HRMS_INVALID_JURISDICTION_BOUNDARY_CODE, ErrorConstants.HRMS_INVALID_JURISDICTION_BOUNDARY_MSG);
			}


	}


	/**
	 * Checks the follwing:
	 * 1. If the dept test is valid.
	 * 2. If the year of passing is valid.
	 * 
	 * @param employee
	 * @param errorMap
	 * @param mdmsData
	 */
	private void validateDepartmentalTest(Employee employee, Map<String, String> errorMap, Map<String, List<String>> mdmsData) {
		if(!CollectionUtils.isEmpty(employee.getTests())) {
			for (DepartmentalTest test : employee.getTests()) {
				if (null!=test.getTest() && !mdmsData.get(HRMSConstants.HRMS_MDMS_DEPT_TEST_CODE).contains(test.getTest()))
					errorMap.put(ErrorConstants.HRMS_INVALID_DEPARTMENTAL_TEST_CODE, ErrorConstants.HRMS_INVALID_DEPARTMENTAL_TEST_MSG );
				if (null!= test.getYearOfPassing() && test.getYearOfPassing() > new Date().getTime()) {
					errorMap.put(ErrorConstants.HRMS_INVALID_DEPARTMENTAL_TEST_PASSING_YEAR_CODE, ErrorConstants.HRMS_INVALID_DEPARTMENTAL_TEST_PASSING_YEAR_MSG);
				}

			}
		}
	}

	/**
	 * Validates if the deactivation details are provided every time an employee is deactivated.
	 *  @param existingEmp
	 * @param updatedEmployeeData
	 * @param errorMap
	 * @param mdmsData
	 */
	private void validateDeactivationDetails(Employee existingEmp, Employee updatedEmployeeData, Map<String, String> errorMap, Map<String, List<String>> mdmsData){
		if(!CollectionUtils.isEmpty(updatedEmployeeData.getDeactivationDetails())) {
			Date date = new Date();
			Date  currentDateStartTime = Date.from(date.toInstant().atZone(ZoneId.systemDefault())
					.truncatedTo(ChronoUnit.DAYS).toInstant());
			for (DeactivationDetails deactivationDetails : updatedEmployeeData.getDeactivationDetails()) {
				if (deactivationDetails.getId()==null){
					if(updatedEmployeeData.getIsActive()){
						errorMap.put(ErrorConstants.HRMS_INVALID_DEACT_REQUEST_CODE, ErrorConstants.HRMS_INVALID_DEACT_REQUEST_MSG);
					}
				}
				if(deactivationDetails.getEffectiveFrom() > new Date().getTime())
					errorMap.put(ErrorConstants.HRMS_UPDATE_DEACT_DETAILS_INCORRECT_EFFECTIVEFROM_CODE, ErrorConstants.HRMS_UPDATE_DEACT_DETAILS_INCORRECT_EFFECTIVEFROM_MSG);

				if(deactivationDetails.getEffectiveFrom() < currentDateStartTime.getTime())
					errorMap.put(ErrorConstants.HRMS_UPDATE_DEACT_DETAILS_INCORRECT_EFFECTIVEFROM_CODE, ErrorConstants.HRMS_UPDATE_DEACT_DETAILS_INCORRECT_EFFECTIVEFROM_MSG);

				if (! mdmsData.get(HRMSConstants.HRMS_MDMS_DEACT_REASON_CODE).contains(deactivationDetails.getReasonForDeactivation()))
					errorMap.put(ErrorConstants.HRMS_INVALID_DEACT_REASON_CODE, ErrorConstants.HRMS_INVALID_DEACT_REASON_MSG);
			}
		}
	}

	private void validateReactivationDetails(Employee existingEmp, Employee updatedEmployeeData, Map<String, String> errorMap, Map<String, List<String>> mdmsData){
		if(!CollectionUtils.isEmpty(updatedEmployeeData.getReactivationDetails())) {
			for (ReactivationDetails reactivationDetails : updatedEmployeeData.getReactivationDetails()) {
				Boolean isValidDetails = existingEmp.getDeactivationDetails().get(0).getEffectiveFrom() <= reactivationDetails.getEffectiveFrom()
										 && reactivationDetails.getEffectiveFrom() <= new Date().getTime();
				if(!isValidDetails)
					errorMap.put(ErrorConstants.HRMS_UPDATE_REACT_DETAILS_INCORRECT_EFFECTIVEFROM_CODE, ErrorConstants.HRMS_UPDATE_REACT_DETAILS_INCORRECT_EFFECTIVEFROM_MSG);

			}
		}
	}
	
	/**
	 * Validates the employee request for update. Validates the following:
	 * 1. MDMS codes in the request
	 * 2. Performs data consistency checks.
	 * 
	 * @param request
	 */
	public void validateUpdateEmployee(EmployeeRequest request) {
		Map<String, String> errorMap = new HashMap<>();
		Map<String, List<String>> boundaryMap = getBoundaryList(request.getRequestInfo(),request.getEmployees().get(0));
		Map<String, List<String>> mdmsData = mdmsService.getMDMSData(request.getRequestInfo(), request.getEmployees().get(0).getTenantId());
		List <String> uuidList = request.getEmployees().stream().map(Employee :: getUuid).collect(Collectors.toList()); 
		EmployeeResponse existingEmployeeResponse = employeeService.search(EmployeeSearchCriteria.builder().uuids(uuidList)
				.tenantId(request.getEmployees().get(0).getTenantId())
				.build(),request.getRequestInfo());
		List <Employee> existingEmployees = existingEmployeeResponse.getEmployees();
		for(Employee employee: request.getEmployees()){
			if(validateEmployeeForUpdate(employee, errorMap)){
				if(!existingEmployees.isEmpty()){
				Employee existingEmp = existingEmployees.stream().filter(existingEmployee -> existingEmployee.getUuid().equals(employee.getUuid())).findFirst().get();
				validateDataConsistency(employee, errorMap, mdmsData, existingEmp, request.getRequestInfo());
				}
				else
					errorMap.put(ErrorConstants.HRMS_UPDATE_EMPLOYEE_NOT_EXIST_CODE, ErrorConstants.HRMS_UPDATE_EMPLOYEE_NOT_EXIST_MSG);
			}
			validateMdmsData(employee, errorMap, mdmsData,boundaryMap);
		}
		if(!CollectionUtils.isEmpty(errorMap.keySet())) {	
			throw new CustomException(errorMap);
		}


	}

	/**
	 * Checks if the ID, UUID and Code are present in the update request
	 * 
	 * @param employee
	 * @param errorMap
	 * @return
	 */
	private boolean validateEmployeeForUpdate(Employee employee, Map<String, String> errorMap) {
		boolean isvalid = true;
		if(employee.getId() == null){
			errorMap.put(ErrorConstants.HRMS_UPDATE_NULL_ID_CODE, ErrorConstants.HRMS_UPDATE_NULL_ID_MSG);
			isvalid=false;
		}
		if(StringUtils.isEmpty(employee.getCode())){
			errorMap.put(ErrorConstants.HRMS_UPDATE_NULL_CODE_CODE, ErrorConstants.HRMS_UPDATE_NULL_CODE_MSG);
			isvalid=false;
		}
		if(StringUtils.isEmpty(employee.getUuid())){
			errorMap.put(ErrorConstants.HRMS_UPDATE_NULL_UUID_CODE, ErrorConstants.HRMS_UPDATE_NULL_UUID_MSG);
			isvalid=false;
		}

		return isvalid;

	}

	/**
	 * Juridictions once created in the system cannot be deleted, they can however be changed. Validates that condition
	 * 
	 * @param existingEmp
	 * @param updatedEmployeeData
	 * @param errorMap
	 */
	private void validateConsistencyJurisdiction(Employee existingEmp, Employee updatedEmployeeData, Map<String, String> errorMap) {
		boolean check =
				updatedEmployeeData.getJurisdictions().stream()
						.map(jurisdiction -> jurisdiction.getId())
						.collect(Collectors.toList())
						.containsAll(existingEmp.getJurisdictions().stream()
								.map(jurisdiction -> jurisdiction.getId())
								.collect(Collectors.toList()));
		if(!check){
			errorMap.put(ErrorConstants.HRMS_UPDATE_JURISDICTION_INCOSISTENT_CODE, ErrorConstants.HRMS_UPDATE_JURISDICTION_INCOSISTENT_MSG);
		}

	}
	
	/**
	 * Assignments once created in the system cannot be deleted, they can however be changed. Validates that condition
	 *
	 * @param existingEmp
	 * @param updatedEmployeeData
	 * @param errorMap
	 */
	private void validateConsistencyAssignment(Employee existingEmp, Employee updatedEmployeeData, Map<String, String> errorMap) {
		if (updatedEmployeeData.getAssignments() != null && existingEmp.getAssignments() != null) {
			boolean check =
					updatedEmployeeData.getAssignments().stream()
							.map(assignment -> assignment.getId())
							.collect(Collectors.toList())
							.containsAll(existingEmp.getAssignments().stream()
									.map(assignment -> assignment.getId())
									.collect(Collectors.toList()));
			if (!check) {
				errorMap.put(ErrorConstants.HRMS_UPDATE_ASSIGNEMENT_INCOSISTENT_CODE, ErrorConstants.HRMS_UPDATE_ASSIGNEMENT_INCOSISTENT_MSG);
			}
		}
	}

	/**
	 * Dept Test details once created in the system cannot be deleted, they can however be changed. Validates that condition
	 * @param existingEmp
	 * @param updatedEmployeeData
	 * @param errorMap
	 */
	private void validateConsistencyDepartmentalTest(Employee existingEmp, Employee updatedEmployeeData, Map<String, String> errorMap){
		if(!CollectionUtils.isEmpty(updatedEmployeeData.getTests())){
			boolean check =
					updatedEmployeeData.getTests().stream()
							.map(test -> test.getId())
							.collect(Collectors.toList())
							.containsAll(existingEmp.getTests().stream()
									.map(test -> test.getId())
									.collect(Collectors.toList()));
			if(!check){
				errorMap.put(ErrorConstants.HRMS_UPDATE_TESTS_INCOSISTENT_CODE, ErrorConstants.HRMS_UPDATE_TESTS_INCOSISTENT_MSG);
			}
		}

	}

	/**
	 * Education Details once created in the system cannot be deleted, they can however be changed. Validates that condition
	 * 
	 * @param existingEmp
	 * @param updatedEmployeeData
	 * @param errorMap
	 */
	private void validateConsistencyEducationalDetails(Employee existingEmp, Employee updatedEmployeeData, Map<String, String> errorMap){
		if(!CollectionUtils.isEmpty(updatedEmployeeData.getEducation())){
			boolean check =
					updatedEmployeeData.getEducation().stream()
							.map(educationalQualification -> educationalQualification.getId())
							.collect(Collectors.toList())
							.containsAll(existingEmp.getEducation().stream()
									.map(educationalQualification -> educationalQualification.getId())
									.collect(Collectors.toList()));
			if(!check){
				errorMap.put(ErrorConstants.HRMS_UPDATE_EDUCATION_INCOSISTENT_CODE, ErrorConstants.HRMS_UPDATE_EDUCATION_INCOSISTENT_MSG);
			}
		}
	}

	/**
	 * Service History once created in the system cannot be deleted, they can however be changed. Validates that condition
	 * 
	 * @param existingEmp
	 * @param updatedEmployeeData
	 * @param errorMap
	 */
	private void validateConsistencyServiceHistory(Employee existingEmp, Employee updatedEmployeeData, Map<String, String> errorMap){
		if(!CollectionUtils.isEmpty(updatedEmployeeData.getServiceHistory())){
			boolean check =
					updatedEmployeeData.getServiceHistory().stream()
							.map(serviceHistory -> serviceHistory.getId())
							.collect(Collectors.toList())
							.containsAll(existingEmp.getServiceHistory().stream()
									.map(serviceHistory -> serviceHistory.getId())
									.collect(Collectors.toList()));
			if(!check){
				errorMap.put(ErrorConstants.HRMS_UPDATE_SERVICE_HISTORY_INCOSISTENT_CODE, ErrorConstants.HRMS_UPDATE_SERVICE_HISTORY_INCOSISTENT_MSG);
			}

		}

	}

	/**
	 * Documents once created in the system cannot be deleted, they can however be changed. Validates that condition
	 * 
	 * @param existingEmp
	 * @param updatedEmployeeData
	 * @param errorMap
	 */
	private void validateConsistencyEmployeeDocument(Employee existingEmp, Employee updatedEmployeeData, Map<String, String> errorMap){
		if(!CollectionUtils.isEmpty(updatedEmployeeData.getDocuments())){
			boolean check =
					updatedEmployeeData.getDocuments().stream()
							.map(employeeDocument -> employeeDocument.getId())
							.collect(Collectors.toList())
							.containsAll(existingEmp.getDocuments().stream()
									.map(employeeDocument -> employeeDocument.getId())
									.collect(Collectors.toList()));
			if (!check) {
				errorMap.put(ErrorConstants.HRMS_UPDATE_DOCUMENT_INCOSISTENT_CODE, ErrorConstants.HRMS_UPDATE_DOCUMENT_INCOSISTENT_MSG);
			}
		}

	}

	/**
	 * Deactivation Details once created in the system cannot be deleted, they can however be changed. Validates that condition
	 * 
	 * @param existingEmp
	 * @param updatedEmployeeData
	 * @param errorMap
	 */
	private void validateConsistencyDeactivationDetails(Employee existingEmp, Employee updatedEmployeeData, Map<String, String> errorMap){
		if(!CollectionUtils.isEmpty(updatedEmployeeData.getDeactivationDetails())){
			boolean check =
					updatedEmployeeData.getDeactivationDetails().stream()
							.map(deactivationDetails -> deactivationDetails.getId())
							.collect(Collectors.toList())
							.containsAll(existingEmp.getDeactivationDetails().stream()
									.map(employeeDocument -> employeeDocument.getId())
									.collect(Collectors.toList()));
			if (!check) {
				errorMap.put(ErrorConstants.HRMS_UPDATE_DEACT_DETAILS_INCOSISTENT_CODE, ErrorConstants.HRMS_UPDATE_DEACT_DETAILS_INCOSISTENT_MSG);
			}
		}

	}

	public void validateEmployeeCountRequest(String tenantId){
		Map<String, String> errorMap = new HashMap<>();
		if(StringUtils.isEmpty(tenantId))
			errorMap.put(ErrorConstants.HRMS_EMPLOYEE_COUNT_ERROR_CODE, ErrorConstants.HRMS_EMPLOYEE_COUNT_ERROR_MSG);

		if(!CollectionUtils.isEmpty(errorMap.keySet())) {
			throw new CustomException(errorMap);
		}
	}

}
