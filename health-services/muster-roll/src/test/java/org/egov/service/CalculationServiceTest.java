package org.egov.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.config.MusterRollServiceConfiguration;
import org.egov.helper.MusterRollRequestBuilderTest;
import org.egov.tracer.model.CustomException;
import org.egov.util.MdmsUtil;
import org.egov.util.MusterRollServiceUtil;
import org.egov.web.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

/**@ExtendWith(MockitoExtension.class)
@Slf4j
public class CalculationServiceTest {

    @InjectMocks
    private CalculationService calculationService;
    @Mock
    private MdmsUtil mdmsUtils;
    @Mock
    private MusterRollServiceConfiguration config;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private MusterRollServiceUtil musterRollServiceUtil;


    @BeforeEach
    void setUp() throws Exception {
        //MOCK MDMS Response
        Object mdmsResponse = MusterRollRequestBuilderTest.getMdmsResponse();
        lenient().when(mdmsUtils.mDMSCallMuster(any(MusterRollRequest.class),
                        any(String.class))).thenReturn(mdmsResponse);
        lenient().when(config.getTimeZone()).thenReturn("Asia/Kolkata");
    }

    @Test
    void shouldCalculateAttendance_IfAttendanceLogsPresent(){
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateSuccess();
        getMockAttendanceLogsSuccess();
        getMockIndividualSuccess();
        getMockBankDetailsSuccess();
        getMockAttendanceRegister();
        //calculationService.createAttendance(musterRollRequest,true);
        assertEquals(2, musterRollRequest.getMusterRoll().getIndividualEntries().size());
    }

    @Test
    void shouldCalculateHalfDayAttendance_IfWorkHours_2(){
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateSuccess();
        getMockAttendanceLogsSuccess();
        getMockIndividualSuccess();
        getMockBankDetailsSuccess();
        //calculationService.createAttendance(musterRollRequest,true);
        AttendanceEntry attendanceEntry = musterRollRequest.getMusterRoll().getIndividualEntries().get(0).getAttendanceEntries().get(0);
        assertEquals(new BigDecimal("0.5"),attendanceEntry.getAttendance());
    }

    @Test
    void shouldCalculateFullDayAttendance_IfWorkHours_6(){
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateSuccess();
        getMockAttendanceLogsSuccess();
        getMockIndividualSuccess();
        getMockBankDetailsSuccess();
        //calculationService.createAttendance(musterRollRequest,true);
        AttendanceEntry attendanceEntry = musterRollRequest.getMusterRoll().getIndividualEntries().get(0).getAttendanceEntries().get(2);
        assertEquals(new BigDecimal("1.0"),attendanceEntry.getAttendance());
    }

    @Test
    void shouldCalculateZeroAttendance_IfNoAttendanceLogged(){
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateSuccess();
        getMockAttendanceLogsSuccess();
        getMockIndividualSuccess();
        getMockBankDetailsSuccess();
        //calculationService.createAttendance(musterRollRequest,true);
        AttendanceEntry attendanceEntry = musterRollRequest.getMusterRoll().getIndividualEntries().get(0).getAttendanceEntries().get(3);
        assertEquals(new BigDecimal("0.0"),attendanceEntry.getAttendance());
    }

    @Test
    void shouldCalculateTotalAttendanceAs2_IfSuccess(){
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateSuccess();
        getMockAttendanceLogsSuccess();
        getMockIndividualSuccess();
        getMockBankDetailsSuccess();
        //calculationService.createAttendance(musterRollRequest,true);
        BigDecimal totalAttendance = musterRollRequest.getMusterRoll().getIndividualEntries().get(0).getActualTotalAttendance();
        assertEquals(new BigDecimal("2.0"),totalAttendance);
    }

    @Test
    void shouldThrowException_IfNoAttendanceLogsFound(){
        MusterRollRequest musterRollRequest = MusterRollRequestBuilderTest.builder().withMusterForCreateException();
        getMockAttendanceLogsFailure();
        CustomException exception = assertThrows(CustomException.class, ()-> calculationService.createAttendance(musterRollRequest,true));
        assertTrue(exception.getCode().contentEquals("ATTENDANCE_LOG_EMPTY"));
    }

    void getMockAttendanceLogsSuccess() {
        //MOCK Attendance log search service response
        lenient().when(config.getAttendanceLogHost()).thenReturn("http://localhost:8023");
        lenient().when(config.getAttendanceLogEndpoint()).thenReturn("/attendance/log/v1/_search");
        AttendanceLogResponse attendanceLogResponse = MusterRollRequestBuilderTest.getAttendanceLogResponse();
        lenient().when(restTemplate.postForObject(any(String.class),any(Object.class),eq(AttendanceLogResponse.class))).
                thenReturn(attendanceLogResponse);
    }

    void getMockAttendanceLogsFailure() {
        //MOCK Attendance log search service response
        lenient().when(config.getAttendanceLogHost()).thenReturn("http://localhost:8023");
        lenient().when(config.getAttendanceLogEndpoint()).thenReturn("/attendance/log/v1/_search");
        AttendanceLogResponse attendanceLogResponse = null;
        lenient().when(restTemplate.postForObject(any(String.class),any(Object.class),eq(AttendanceLogResponse.class))).
                thenReturn(attendanceLogResponse);
    }
    
    void getMockAttendanceRegister() {
        //MOCK Attendance log search service response
        lenient().when(config.getAttendanceLogHost()).thenReturn("http://localhost:8023");
        lenient().when(config.getAttendanceRegisterEndpoint()).thenReturn("/attendance/v1/_search");
        AttendanceRegisterResponse attendanceResponse = MusterRollRequestBuilderTest.getAttendanceRegisterResponse();
        lenient().when(restTemplate.postForObject(any(String.class),any(Object.class),eq(AttendanceRegisterResponse.class))).
                thenReturn(attendanceResponse);
    }

    void getMockIndividualSuccess() {
        //MOCK Attendance log search service response
        lenient().when(config.getIndividualHost()).thenReturn("http://localhost:8023");
        lenient().when(config.getIndividualSearchEndpoint()).thenReturn("/attendance/log/v1/_search");
        IndividualBulkResponse response = MusterRollRequestBuilderTest.getIndividualResponse();
        lenient().when(restTemplate.postForObject(any(String.class),any(Object.class),eq(IndividualBulkResponse.class))).
                thenReturn(response);
    }

    void getMockBankDetailsSuccess() {
        //MOCK Attendance log search service response
        lenient().when(config.getBankaccountsHost()).thenReturn("http://localhost:8023");
        lenient().when(config.getBankaccountsSearchEndpoint()).thenReturn("/attendance/log/v1/_search");
        BankAccountResponse response = MusterRollRequestBuilderTest.getBankDetailsResponse();
        lenient().when(restTemplate.postForObject(any(String.class),any(Object.class),eq(BankAccountResponse.class))).
                thenReturn(response);
    }

}**/
