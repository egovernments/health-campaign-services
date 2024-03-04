package org.egov.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.Main;
import org.egov.TestConfiguration;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.helper.AttendanceRegisterBuilderTest;
import org.egov.helper.AttendanceRegisterRequestBuilderTest;
import org.egov.repository.AttendanceLogRepository;
import org.egov.repository.AttendeeRepository;
import org.egov.repository.RegisterRepository;
import org.egov.repository.StaffRepository;
import org.egov.service.AttendanceRegisterService;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ErrorRes;
import org.egov.util.ResponseInfoFactory;
import org.egov.web.models.AttendanceRegisterRequest;
import org.egov.web.models.AttendanceRegisterResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API tests for AttendanceApiController
 */
@ContextConfiguration(classes = Main.class)
@WebMvcTest(AttendanceApiController.class)
@Import(TestConfiguration.class)
@AutoConfigureMockMvc
public class AttendanceApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HttpServletRequest request;

    @MockBean
    private ResponseInfoFactory responseInfoFactory;

    @MockBean
    private AttendanceLogRepository attendanceLogRepository;

    @MockBean
    private AttendanceRegisterService attendanceRegisterService;

    @MockBean
    private AttendeeRepository attendeeRepository;

    @MockBean
    private RegisterRepository registerRepository;

    @MockBean
    private StaffRepository staffRepository;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should pass for correct API operation for create attendance register")
    public void RegisterCreatePostSuccess() throws Exception {

        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().addGoodRegister()
                .requestInfoWithoutUserInfo().build();
        ResponseInfo responseInfo = AttendanceRegisterBuilderTest.getResponseInfo_Success();

        when(attendanceRegisterService.createAttendanceRegister(any(AttendanceRegisterRequest.class))).thenReturn(attendanceRegisterRequest);
        when(responseInfoFactory.createResponseInfoFromRequestInfo(any(RequestInfo.class), eq(true))).thenReturn(responseInfo);

        ObjectMapper objectMapper = new ObjectMapper();
        String content = objectMapper.writeValueAsString(attendanceRegisterRequest);
        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(content))
                .andExpect(status().isOk()).andReturn();
        String responseStr = result.getResponse().getContentAsString();

        AttendanceRegisterResponse response = objectMapper.readValue(responseStr, AttendanceRegisterResponse.class);

        assertEquals("successful", response.getResponseInfo().getStatus());

    }

    @Test
    @DisplayName("should fail for incomplete attendance register object in API request")
    public void RegisterCreatePostFailure() throws Exception {

        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().attendanceRegisterWithoutStartDate()
                .withRequestInfo().build();

        when(attendanceRegisterService.createAttendanceRegister(any(AttendanceRegisterRequest.class)))
                .thenThrow(new CustomException("START_DATE", "Start date is mandatory"));

        ObjectMapper objectMapper = new ObjectMapper();
        String content = objectMapper.writeValueAsString(attendanceRegisterRequest);
        MvcResult result = mockMvc.perform(post("/v1/_create").contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isBadRequest()).andReturn();

        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals("Start date is mandatory", response.getErrors().get(0).getMessage());
    }
}
