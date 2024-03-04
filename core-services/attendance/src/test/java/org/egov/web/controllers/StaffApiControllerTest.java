package org.egov.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.Main;
import org.egov.TestConfiguration;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.helper.StaffRequestBuilderTest;
import org.egov.repository.AttendanceLogRepository;
import org.egov.service.StaffService;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ErrorRes;
import org.egov.util.ResponseInfoFactory;
import org.egov.web.models.StaffPermissionRequest;
import org.egov.web.models.StaffPermissionResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ContextConfiguration(classes = Main.class)
@WebMvcTest(StaffApiController.class)
@Import(TestConfiguration.class)
@AutoConfigureMockMvc
public class StaffApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HttpServletRequest request;

    @MockBean
    private ResponseInfoFactory responseInfoFactory;

    @MockBean
    private StaffService staffService;

    @MockBean
    private AttendanceLogRepository attendanceLogRepository;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should pass for correct API operation")
    public void staffCreatePostSuccess() throws Exception {

        StaffPermissionRequest staffPermissionRequest = StaffRequestBuilderTest.getStaffPermissionRequest();
        ResponseInfo responseInfo = StaffRequestBuilderTest.getResponseInfo_Success();

        when(staffService.createAttendanceStaff(any(StaffPermissionRequest.class), eq(false))).thenReturn(staffPermissionRequest);
        when(responseInfoFactory.createResponseInfoFromRequestInfo(any(RequestInfo.class), eq(true))).thenReturn(responseInfo);

        ObjectMapper objectMapper = new ObjectMapper();
        String content = objectMapper.writeValueAsString(staffPermissionRequest);
        MvcResult result = mockMvc.perform(post("/staff/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(content))
                .andExpect(status().isOk()).andReturn();
        String responseStr = result.getResponse().getContentAsString();

        StaffPermissionResponse response = objectMapper.readValue(responseStr, StaffPermissionResponse.class);

        assertEquals("successful", response.getResponseInfo().getStatus());

    }

    @Test
    @DisplayName("should fail for incomplete staff object in API request")
    public void staffCreatePostFailure() throws Exception {

        StaffPermissionRequest staffPermissionRequest = StaffRequestBuilderTest.getStaffPermissionRequest();
        staffPermissionRequest.setStaff(null);

        when(staffService.createAttendanceStaff(any(StaffPermissionRequest.class), eq(false))).thenThrow(new CustomException("STAFF", "Staff is mandatory"));

        ObjectMapper objectMapper = new ObjectMapper();
        String content = objectMapper.writeValueAsString(staffPermissionRequest);
        MvcResult result=mockMvc.perform(post("/staff/v1/_create").contentType(MediaType.APPLICATION_JSON).content(content))
                .andExpect(status().isBadRequest()).andReturn();

        String responseStr = result.getResponse().getContentAsString();
        ErrorRes response  = objectMapper.readValue(responseStr,
                ErrorRes.class);

        assertEquals("Staff is mandatory",response.getErrors().get(0).getMessage());
    }
}
