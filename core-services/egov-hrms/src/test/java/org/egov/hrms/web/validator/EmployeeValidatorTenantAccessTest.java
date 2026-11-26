package org.egov.hrms.web.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.hrms.config.PropertiesManager;
import org.egov.hrms.model.Employee;
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
import org.egov.hrms.web.contract.User;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeValidatorTenantAccessTest {

	@Mock
	private MDMSService mdmsService;

	@Mock
	private EmployeeService employeeService;

	@Mock
	private UserService userService;

	@Mock
	private PropertiesManager propertiesManager;

	@Mock
	private HRMSUtils hrmsUtils;

	@Mock
	private RestCallRepository restCallRepository;

	@InjectMocks
	private EmployeeValidator validator;

	private static RequestInfo requestInfoWithRoleTenant(String roleTenantId) {
		return RequestInfo.builder()
				.userInfo(org.egov.common.contract.request.User.builder().uuid("u1").type("EMPLOYEE")
						.roles(Collections.singletonList(Role.builder().code("HRMS_ADMIN").tenantId(roleTenantId).build()))
						.build())
				.build();
	}

	private static Employee employee(String tenantId, String userTenantId) {
		return Employee.builder()
				.tenantId(tenantId)
				.user(User.builder().mobileNumber("9999999999").tenantId(userTenantId).build())
				.build();
	}

	private static EmployeeRequest request(RequestInfo requestInfo, Employee... employees) {
		List<Employee> list = new ArrayList<>();
		Collections.addAll(list, employees);
		return EmployeeRequest.builder().requestInfo(requestInfo).employees(list).build();
	}

	private static void assertDenied(Runnable call) {
		CustomException e = assertThrows(CustomException.class, call::run);
		assertEquals(ErrorConstants.INVALID_TENANT_ACCESS, e.getCode());
		assertEquals("User does not have access to the requested tenant", e.getMessage());
	}

	// ---- search ----

	@Test
	void searchAllowsTenantCoveredByRole() {
		EmployeeSearchCriteria criteria = EmployeeSearchCriteria.builder().tenantId("pb.amritsar")
				.codes(Collections.singletonList("EMP-1")).build();
		assertDoesNotThrow(() -> validator.validateSearchRequest(requestInfoWithRoleTenant("pb"), criteria));
	}

	@Test
	void searchRejectsTenantNotCoveredByRole() {
		EmployeeSearchCriteria criteria = EmployeeSearchCriteria.builder().tenantId("mz")
				.codes(Collections.singletonList("EMP-1")).build();
		assertDenied(() -> validator.validateSearchRequest(requestInfoWithRoleTenant("pb"), criteria));
	}

	@Test
	void searchRejectsSiblingPrefixTenant() {
		EmployeeSearchCriteria criteria = EmployeeSearchCriteria.builder().tenantId("pbx")
				.codes(Collections.singletonList("EMP-1")).build();
		assertDenied(() -> validator.validateSearchRequest(requestInfoWithRoleTenant("pb"), criteria));
	}

	@Test
	void searchAllowsRequestWithoutRolesAsInternalCall() {
		EmployeeSearchCriteria criteria = EmployeeSearchCriteria.builder().tenantId("mz")
				.codes(Collections.singletonList("EMP-1")).build();
		assertDoesNotThrow(() -> validator.validateSearchRequest(RequestInfo.builder().build(), criteria));
	}

	@Test
	void searchWithoutTenantIsRejected() {
		for (String tenant : new String[] {null, "", "   ", "\t"}) {
			EmployeeSearchCriteria criteria = EmployeeSearchCriteria.builder()
					.codes(Collections.singletonList("EMP-1")).tenantId(tenant).build();
			CustomException e = assertThrows(CustomException.class,
					() -> validator.validateSearchRequest(requestInfoWithRoleTenant("pb"), criteria));
			assertEquals(ErrorConstants.HRMS_INVALID_SEARCH_TENANT_CODE, e.getCode());
		}
	}

	@Test
	void searchWithoutTenantIsRejectedForInternalCallToo() {
		for (String tenant : new String[] {null, "", "   "}) {
			EmployeeSearchCriteria criteria = EmployeeSearchCriteria.builder()
					.codes(Collections.singletonList("EMP-1")).tenantId(tenant).build();
			CustomException e = assertThrows(CustomException.class,
					() -> validator.validateSearchRequest(RequestInfo.builder().build(), criteria));
			assertEquals(ErrorConstants.HRMS_INVALID_SEARCH_TENANT_CODE, e.getCode());
		}
	}

	// ---- create ----

	@Test
	void createRejectsEmployeeTenantNotCoveredByRole() {
		EmployeeRequest request = request(requestInfoWithRoleTenant("pb"), employee("mz", null));
		assertDenied(() -> validator.validateCreateEmployee(request, new ArrayList<>()));
		verifyNoInteractions(mdmsService, userService, hrmsUtils, restCallRepository, employeeService);
	}

	@Test
	void createRejectsUserTenantNotCoveredByRole() {
		EmployeeRequest request = request(requestInfoWithRoleTenant("pb"), employee("pb.amritsar", "mz"));
		assertDenied(() -> validator.validateCreateEmployee(request, new ArrayList<>()));
		verifyNoInteractions(mdmsService, userService, hrmsUtils, restCallRepository, employeeService);
	}

	@Test
	void createRejectsWholeBatchWhenOneEmployeeIsNotCovered() {
		EmployeeRequest request = request(requestInfoWithRoleTenant("pb"),
				employee("pb.amritsar", null), employee("mz", null));
		assertDenied(() -> validator.validateCreateEmployee(request, new ArrayList<>()));
		verifyNoInteractions(mdmsService, userService, hrmsUtils, restCallRepository, employeeService);
	}

	@Test
	void createAllowsRequestWithoutRolesAsInternalCall() {
		when(propertiesManager.isAutoGeneratePassword()).thenReturn(true);
		EmployeeRequest request = request(RequestInfo.builder().build(), employee("mz", "mz"));
		assertDoesNotThrow(() -> validator.validateCreateEmployee(request, new ArrayList<>()));
		verify(mdmsService).getMDMSData(any(RequestInfo.class), any(String.class));
	}

	@Test
	void createAllowsCoveredTenantsAndContinuesToExistingValidation() {
		when(propertiesManager.isAutoGeneratePassword()).thenReturn(true);
		EmployeeRequest request = request(requestInfoWithRoleTenant("pb"), employee("pb.amritsar", "pb.amritsar"));
		assertDoesNotThrow(() -> validator.validateCreateEmployee(request, new ArrayList<>()));
		verify(mdmsService).getMDMSData(any(RequestInfo.class), any(String.class));
	}

	private static Employee employeeWithRoleTenants(String tenantId, String... roleTenants) {
		Employee employee = employee(tenantId, tenantId);
		List<org.egov.hrms.model.Role> roles = new ArrayList<>();
		for (String roleTenant : roleTenants)
			roles.add(org.egov.hrms.model.Role.builder().code("HRMS_ADMIN").name("HRMS Admin").tenantId(roleTenant).build());
		employee.getUser().setRoles(roles);
		return employee;
	}

	@Test
	void createRejectsRoleTenantNotCoveredByCaller() {
		EmployeeRequest request = request(requestInfoWithRoleTenant("pb"), employeeWithRoleTenants("pb", "pb", "mz"));
		assertDenied(() -> validator.validateCreateEmployee(request, new ArrayList<>()));
		verifyNoInteractions(mdmsService, userService, hrmsUtils, restCallRepository, employeeService);
	}

	@Test
	void updateRejectsRoleTenantNotCoveredByCaller() {
		EmployeeRequest request = request(requestInfoWithRoleTenant("pb"), employeeWithRoleTenants("pb", "mz"));
		assertDenied(() -> validator.validateUpdateEmployee(request));
		verifyNoInteractions(mdmsService, userService, hrmsUtils, restCallRepository, employeeService);
	}

	@Test
	void createAllowsRoleTenantsCoveredByCallerAndRolesWithoutTenant() {
		when(propertiesManager.isAutoGeneratePassword()).thenReturn(true);
		EmployeeRequest request = request(requestInfoWithRoleTenant("pb"), employeeWithRoleTenants("pb", "pb", "pb.amritsar", null));
		assertDoesNotThrow(() -> validator.validateCreateEmployee(request, new ArrayList<>()));
		verify(mdmsService).getMDMSData(any(RequestInfo.class), any(String.class));
	}

	@Test
	void createDoesNotApplyRuleToMissingUserTenant() {
		when(propertiesManager.isAutoGeneratePassword()).thenReturn(true);
		EmployeeRequest request = request(requestInfoWithRoleTenant("pb"), employee("pb", null));
		assertDoesNotThrow(() -> validator.validateCreateEmployee(request, new ArrayList<>()));
		verify(mdmsService).getMDMSData(any(RequestInfo.class), any(String.class));
	}

	// ---- update ----

	@Test
	void updateRejectsEmployeeTenantNotCoveredByRole() {
		EmployeeRequest request = request(requestInfoWithRoleTenant("pb"), employee("mz", null));
		assertDenied(() -> validator.validateUpdateEmployee(request));
		verifyNoInteractions(mdmsService, userService, hrmsUtils, restCallRepository, employeeService);
	}

	@Test
	void updateRejectsUserTenantNotCoveredByRole() {
		EmployeeRequest request = request(requestInfoWithRoleTenant("pb"), employee("pb.amritsar", "pbx"));
		assertDenied(() -> validator.validateUpdateEmployee(request));
		verifyNoInteractions(mdmsService, userService, hrmsUtils, restCallRepository, employeeService);
	}

	@Test
	void updateAllowsRequestWithoutRolesAsInternalCall() {
		when(employeeService.search(any(EmployeeSearchCriteria.class), any(RequestInfo.class)))
				.thenReturn(EmployeeResponse.builder().employees(new ArrayList<>()).build());
		when(mdmsService.getMDMSData(any(RequestInfo.class), any(String.class)))
				.thenReturn(Collections.singletonMap(HRMSConstants.HRMS_MDMS_EMP_TYPE_CODE, Collections.singletonList("PERMANENT")));
		// The employee has no id/code/uuid, so the existing update validation (not the tenant rule) rejects it.
		EmployeeRequest request = request(RequestInfo.builder().build(), employee("mz", "mz"));
		CustomException e = assertThrows(CustomException.class, () -> validator.validateUpdateEmployee(request));
		assertTrue(e.getErrors().containsKey(ErrorConstants.HRMS_UPDATE_NULL_ID_CODE));
		assertFalse(e.getErrors().containsKey(ErrorConstants.INVALID_TENANT_ACCESS));
		verify(mdmsService).getMDMSData(any(RequestInfo.class), any(String.class));
	}

	@Test
	void updateAllowsCoveredTenantsAndContinuesToExistingValidation() {
		when(employeeService.search(any(EmployeeSearchCriteria.class), any(RequestInfo.class)))
				.thenReturn(EmployeeResponse.builder().employees(new ArrayList<>()).build());
		when(mdmsService.getMDMSData(any(RequestInfo.class), any(String.class)))
				.thenReturn(Collections.singletonMap(HRMSConstants.HRMS_MDMS_EMP_TYPE_CODE, Collections.singletonList("PERMANENT")));
		// The employee has no id/code/uuid, so the existing update validation (not the tenant rule) rejects it.
		EmployeeRequest request = request(requestInfoWithRoleTenant("pb"), employee("pb.amritsar", "pb.amritsar"));
		CustomException e = assertThrows(CustomException.class, () -> validator.validateUpdateEmployee(request));
		assertTrue(e.getErrors().containsKey(ErrorConstants.HRMS_UPDATE_NULL_ID_CODE));
		assertFalse(e.getErrors().containsKey(ErrorConstants.INVALID_TENANT_ACCESS));
		verify(mdmsService).getMDMSData(any(RequestInfo.class), any(String.class));
	}

	// ---- count ----

	@Test
	void countRejectsTenantNotCoveredByRole() {
		assertDenied(() -> validator.validateEmployeeCountRequest(requestInfoWithRoleTenant("pb"), "mz"));
	}

	@Test
	void countAllowsSameTenantAsRole() {
		assertDoesNotThrow(() -> validator.validateEmployeeCountRequest(requestInfoWithRoleTenant("pb"), "pb"));
	}

	@Test
	void countAllowsChildTenantOfStateLevelRole() {
		assertDoesNotThrow(() -> validator.validateEmployeeCountRequest(requestInfoWithRoleTenant("pb"), "pb.amritsar"));
	}

	@Test
	void countWithEmptyTenantGivesExistingCountError() {
		for (String emptyTenant : new String[] { null, "" }) {
			CustomException e = assertThrows(CustomException.class,
					() -> validator.validateEmployeeCountRequest(requestInfoWithRoleTenant("pb"), emptyTenant));
			assertTrue(e.getErrors().containsKey(ErrorConstants.HRMS_EMPLOYEE_COUNT_ERROR_CODE));
			assertFalse(e.getErrors().containsKey(ErrorConstants.INVALID_TENANT_ACCESS));
		}
	}

	@Test
	void countAllowsRequestWithoutRolesAsInternalCall() {
		assertDoesNotThrow(() -> validator.validateEmployeeCountRequest(RequestInfo.builder().build(), "mz"));
		assertDoesNotThrow(() -> validator.validateEmployeeCountRequest(null, "mz"));
	}
}
