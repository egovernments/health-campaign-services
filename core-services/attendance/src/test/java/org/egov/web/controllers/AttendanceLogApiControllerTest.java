package org.egov.web.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.Main;
import org.egov.TestConfiguration;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.helper.AttendanceLogRequestTestBuilder;
import org.egov.kafka.Producer;
import org.egov.repository.AttendanceLogRepository;
import org.egov.service.AttendanceLogService;
import org.egov.util.ResponseInfoFactory;
import org.egov.web.models.AttendanceLogRequest;
import org.egov.web.models.AttendanceLogResponse;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;



@ContextConfiguration(classes = Main.class)
@WebMvcTest(AttendanceLogApiController.class)
@Import(TestConfiguration.class)
@AutoConfigureMockMvc

public class AttendanceLogApiControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AttendanceLogService attendanceLogService;

    @MockBean
    private ResponseInfoFactory responseInfoFactory;

    @MockBean
    private Producer producer;

    @MockBean
    private AttendanceLogRepository attendanceLogRepository;

    @MockBean
    private JdbcTemplate jdbcTemplate;


    @DisplayName("attendance log request should pass and create attendance log")
    @Test
    public void attendanceLogV1CreatePOSTSuccess() throws Exception{
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();

        ResponseInfo responseInfo = ResponseInfo.builder()
                .apiId(attendanceLogRequest.getRequestInfo().getApiId())
                .ver(attendanceLogRequest.getRequestInfo().getVer())
                .ts(attendanceLogRequest.getRequestInfo().getTs())
                .resMsgId("uief87324")
                .msgId(attendanceLogRequest.getRequestInfo().getMsgId())
                .status("successful").build();

        AttendanceLogResponse attendanceLogResponse = AttendanceLogResponse.builder().responseInfo(responseInfo).attendance(attendanceLogRequest.getAttendance()).build();


        when(attendanceLogService.createAttendanceLog(any(AttendanceLogRequest.class))).thenReturn(attendanceLogResponse);

        MvcResult result = mockMvc.perform(post("/log/v1/_create").contentType(MediaType
                        .APPLICATION_JSON).content(objectMapper.writeValueAsString(attendanceLogRequest)))
                .andExpect(status().isOk()).andReturn();

        String responseStr = result.getResponse().getContentAsString();
        AttendanceLogResponse response = objectMapper.readValue(responseStr,
                AttendanceLogResponse.class);

        assertEquals(1, response.getAttendance().size());
        assertNotNull(response.getAttendance().get(0).getId());
        assertEquals("successful", response.getResponseInfo().getStatus());
   }
}
