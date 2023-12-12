package org.egov.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.config.AttendanceServiceConfiguration;
import org.egov.enrichment.AttendanceLogEnrichment;
import org.egov.helper.AttendanceLogRequestTestBuilder;
import org.egov.kafka.Producer;
import org.egov.util.ResponseInfoFactory;
import org.egov.validator.AttendanceLogServiceValidator;
import org.egov.web.models.AttendanceLogRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class AttendanceLogServiceTest {

    @InjectMocks
    private AttendanceLogService attendanceLogService;

    @Mock
    private AttendanceLogServiceValidator attendanceLogServiceValidator;


    @Mock
    private ResponseInfoFactory responseInfoFactory;


    @Mock
    private AttendanceLogEnrichment attendanceLogEnricher;

    @Mock
    private Producer producer;

    @Mock
    private AttendanceServiceConfiguration config;


    @DisplayName("create attendance log successfully")
    @Test
    public void createAttendanceLogTest_1(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        when(config.getCreateAttendanceLogTopic()).thenReturn("save-attendance-log");

        attendanceLogService.createAttendanceLog(attendanceLogRequest);

        verify(attendanceLogServiceValidator, times(1)).validateCreateAttendanceLogRequest(attendanceLogRequest);

        verify(attendanceLogEnricher, times(1)).enrichAttendanceLogCreateRequest(attendanceLogRequest);

        verify(producer, times(1)).push(eq("save-attendance-log"), any(AttendanceLogRequest.class));

        assertNotNull(attendanceLogRequest.getAttendance());
    }
}
