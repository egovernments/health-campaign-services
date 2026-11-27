package org.egov.hrms.web.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.request.RequestInfo;
import org.egov.hrms.service.EmployeeService;
import org.egov.hrms.web.validator.EmployeeValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks which RequestInfo the _count endpoint reads from the request body.
 * The gateway puts the logged in user inside "RequestInfo", so that object must reach the tenant check.
 */
class EmployeeControllerCountTest {

	private MockMvc mockMvc;
	private EmployeeValidator validator;
	private EmployeeService employeeService;

	@BeforeEach
	void setUp() {
		EmployeeController controller = new EmployeeController();
		validator = mock(EmployeeValidator.class);
		employeeService = mock(EmployeeService.class);
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		ReflectionTestUtils.setField(controller, "validator", validator);
		ReflectionTestUtils.setField(controller, "employeeService", employeeService);
		ReflectionTestUtils.setField(controller, "objectMapper", objectMapper);
		when(employeeService.getEmployeeCountResponse(any(), any())).thenReturn(new HashMap<>());
		mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
	}

	private RequestInfo countAndCaptureRequestInfo(String body, String tenantId) throws Exception {
		mockMvc.perform(post("/employees/_count").param("tenantId", tenantId)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk());
		ArgumentCaptor<RequestInfo> captor = ArgumentCaptor.forClass(RequestInfo.class);
		verify(validator).validateEmployeeCountRequest(captor.capture(), eq(tenantId));
		return captor.getValue();
	}

	@Test
	void wrappedRequestInfoWithUserReachesTheTenantCheck() throws Exception {
		String body = "{\"RequestInfo\":{\"apiId\":\"Rainmaker\",\"userInfo\":{\"uuid\":\"u1\","
				+ "\"roles\":[{\"code\":\"HRMS_ADMIN\",\"tenantId\":\"pb\"}]}},\"tenantId\":\"pb\"}";

		RequestInfo requestInfo = countAndCaptureRequestInfo(body, "mz");

		assertNotNull(requestInfo.getUserInfo());
		assertEquals("u1", requestInfo.getUserInfo().getUuid());
		assertEquals("pb", requestInfo.getUserInfo().getRoles().get(0).getTenantId());
	}

	@Test
	void bareRequestInfoBodyIsStillRead() throws Exception {
		String body = "{\"apiId\":\"Rainmaker\",\"userInfo\":{\"uuid\":\"u2\","
				+ "\"roles\":[{\"code\":\"HRMS_ADMIN\",\"tenantId\":\"pb\"}]}}";

		RequestInfo requestInfo = countAndCaptureRequestInfo(body, "pb");

		assertNotNull(requestInfo.getUserInfo());
		assertEquals("u2", requestInfo.getUserInfo().getUuid());
	}

	@Test
	void nullRequestInfoInWrapperIsPassedAsNull() throws Exception {
		RequestInfo requestInfo = countAndCaptureRequestInfo("{\"RequestInfo\":null}", "pb");

		assertNull(requestInfo);
	}
}
