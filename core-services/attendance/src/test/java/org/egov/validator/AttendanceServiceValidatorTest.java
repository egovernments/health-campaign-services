package org.egov.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.helper.AttendanceRegisterRequestBuilderTest;
import org.egov.helper.AttendeeRequestBuilderTest;
import org.egov.repository.RegisterRepository;
import org.egov.tracer.model.CustomException;
import org.egov.util.MDMSUtils;
import org.egov.web.models.AttendanceRegister;
import org.egov.web.models.AttendanceRegisterRequest;
import org.egov.web.models.AttendanceRegisterSearchCriteria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class AttendanceServiceValidatorTest {

    @InjectMocks
    private AttendanceServiceValidator attendanceServiceValidator;

    @Mock
    private MDMSUtils mdmsUtils;

    @Mock
    private RegisterRepository registerRepository;

    @DisplayName("Method validateRequestInfo: With good request")
    @Test
    public void validateCreateAttendanceRegister_validateRequestInfo_1(){
        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().withRequestInfo().addGoodRegister().build();
        assertDoesNotThrow(()-> ReflectionTestUtils.invokeMethod(attendanceServiceValidator, "validateRequestInfo", attendanceRegisterRequest.getRequestInfo(), new HashMap<>()));
    }

    @DisplayName("Method validateRequestInfo: Should throw exception with error code REQUEST_INFO")
    @Test
    public void validateCreateAttendanceRegister_validateRequestInfo_2(){
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceServiceValidator, "validateRequestInfo", null,new HashMap<>()));
        assertTrue(exception.getCode().equals("REQUEST_INFO"));
    }

    @DisplayName("Method validateRequestInfo: Should throw exception with error code USERINFO")
    @Test
    public void validateCreateAttendanceRegister_validateRequestInfo_3(){
        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().requestInfoWithoutUserInfo().addGoodRegister().build();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceServiceValidator, "validateRequestInfo", attendanceRegisterRequest.getRequestInfo(),new HashMap<>()));
        assertTrue(exception.getCode().equals("USERINFO"));
    }

    @DisplayName("Method validateRequestInfo: Should throw exception with error code USERINFO")
    @Test
    public void validateCreateAttendanceRegister_validateRequestInfo_4(){
        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().requestInfoWithUserInfoButWithOutUUID().build();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceServiceValidator, "validateRequestInfo", attendanceRegisterRequest.getRequestInfo(),new HashMap<>()));
        assertTrue(exception.getCode().equals("USERINFO_UUID"));
    }

    @DisplayName("Method validateAttendanceRegisterRequest: Should run successfully")
    @Test
    public void validateCreateAttendanceRegister_validateAttendanceRegisterRequest_1(){
        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().addGoodRegister().build();
        assertDoesNotThrow(()-> ReflectionTestUtils.invokeMethod(attendanceServiceValidator, "validateAttendanceRegisterRequest", attendanceRegisterRequest.getAttendanceRegister(),new HashMap<>()));
    }

    @DisplayName("Method validateAttendanceRegisterRequest: Should throw exception with error code ATTENDANCE_REGISTER")
    @Test
    public void validateCreateAttendanceRegister_validateAttendanceRegisterRequest_2(){
        CustomException exception = assertThrows(CustomException.class,()-> ReflectionTestUtils.invokeMethod(attendanceServiceValidator, "validateAttendanceRegisterRequest", null,new HashMap<>()));
        assertTrue(exception.getCode().equals("ATTENDANCE_REGISTER"));
    }

    @DisplayName("Method validateAttendanceRegisterRequest: Should throw exception with error code ATTENDANCE_REGISTER")
    @Test
    public void validateCreateAttendanceRegister_validateAttendanceRegisterRequest_3(){
        // Empty list
        List<AttendanceRegister> attendanceRegisters = new ArrayList<>();
        CustomException exception = assertThrows(CustomException.class,()-> ReflectionTestUtils.invokeMethod(attendanceServiceValidator, "validateAttendanceRegisterRequest", attendanceRegisters,new HashMap<>()));
        assertTrue(exception.getCode().equals("ATTENDANCE_REGISTER"));
    }

    @DisplayName("Method validateAttendanceRegisterRequest: Error code TENANT_ID")
    @Test
    public void validateCreateAttendanceRegister_validateAttendanceRegisterRequest_4(){
        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().attendanceRegisterWithoutTenantId().build();
        List<AttendanceRegister> attendanceRegister = attendanceRegisterRequest.getAttendanceRegister();
        Map<String, String> errorMap = new HashMap<>();
        CustomException exception = assertThrows(CustomException.class,()->ReflectionTestUtils.invokeMethod(attendanceServiceValidator, "validateAttendanceRegisterRequest", attendanceRegister, errorMap));
        assertTrue(exception.getCode().equals("TENANT_ID"));
    }

    @DisplayName("Method validateAttendanceRegisterRequest: Error code NAME")
    @Test
    public void validateCreateAttendanceRegister_validateAttendanceRegisterRequest_5(){
        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().attendanceRegisterWithoutName().build();
        Map<String, String> errorMap = new HashMap<>();
        ReflectionTestUtils.invokeMethod(attendanceServiceValidator, "validateAttendanceRegisterRequest", attendanceRegisterRequest.getAttendanceRegister(),errorMap);
        assertTrue(errorMap.keySet().contains("NAME"));
    }

    @DisplayName("Method validateAttendanceRegisterRequest: Error code START_DATE")
    @Test
    public void validateCreateAttendanceRegister_validateAttendanceRegisterRequest_6(){
        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().attendanceRegisterWithoutStartDate().build();
        Map<String, String> errorMap = new HashMap<>();
        CustomException exception = assertThrows(CustomException.class,()->ReflectionTestUtils.invokeMethod(attendanceServiceValidator, "validateAttendanceRegisterRequest", attendanceRegisterRequest.getAttendanceRegister(),errorMap));
        assertTrue(exception.getCode().equals("START_DATE"));
    }

    @DisplayName("Method validateAttendanceRegisterRequest: Error code DATE")
    @Test
    public void validateCreateAttendanceRegister_validateAttendanceRegisterRequest_7(){
        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().attendanceRegisterWithStartDateGTEndDate().build();
        Map<String, String> errorMap = new HashMap<>();
        ReflectionTestUtils.invokeMethod(attendanceServiceValidator, "validateAttendanceRegisterRequest", attendanceRegisterRequest.getAttendanceRegister(),errorMap);
        assertTrue(errorMap.keySet().contains("DATE"));
    }

    @DisplayName("Method validateAttendanceRegisterRequest: Error code DATE")
    @Test
    public void validateCreateAttendanceRegister_validateMultipleTenantIds(){
        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().withRequestInfo().attendanceRegistersWithMultipleTenantIds().build();
        Map<String, String> errorMap = new HashMap<>();
        CustomException exception = assertThrows(CustomException.class,()->ReflectionTestUtils.invokeMethod(attendanceServiceValidator, "validateCreateAttendanceRegister", attendanceRegisterRequest));
        assertTrue(exception.getCode().equals("MULTIPLE_TENANTS"));
    }

    @DisplayName("Method validateCreateAttendanceRegister: run successfully")
    @Test
    public void validateCreateAttendanceRegister_validateCreateAttendanceRegister_1(){
        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().withRequestInfo().addGoodRegister().build();
        Object mdmsResponse = AttendeeRequestBuilderTest.getMdmsResponseForValidTenant();
        lenient().when(mdmsUtils.mDMSCall(any(RequestInfo.class),
                any(String.class))).thenReturn(mdmsResponse);
        List<AttendanceRegister> registers = new ArrayList<>();
        AttendanceRegister register = AttendanceRegister.builder().build();
        registers.add(register);
        when(registerRepository.getRegister(any(AttendanceRegisterSearchCriteria.class))).thenReturn(registers);
        CustomException exception = assertThrows(CustomException.class, ()->attendanceServiceValidator.validateCreateAttendanceRegister(attendanceRegisterRequest));
        assertTrue(exception.getCode().equals("REGISTER_ALREADY_EXISTS"));
    }

    @Test
    public void validateCreateAttendanceRegister_validateCreateAttendanceRegister_3(){
        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().withRequestInfo().addGoodRegister().build();
        Object mdmsResponse = AttendeeRequestBuilderTest.getMdmsResponseForValidTenant();
        lenient().when(mdmsUtils.mDMSCall(any(RequestInfo.class),
                any(String.class))).thenReturn(mdmsResponse);
        List<AttendanceRegister> registers = new ArrayList<>();
        when(registerRepository.getRegister(any(AttendanceRegisterSearchCriteria.class))).thenReturn(registers);
        attendanceServiceValidator.validateCreateAttendanceRegister(attendanceRegisterRequest);
    }

    @DisplayName("Method validateCreateAttendanceRegister: Error code INVALID_TENANT")
    @Test
    public void validateCreateAttendanceRegister_validateCreateAttendanceRegister_2(){
        AttendanceRegisterRequest attendanceRegisterRequest = AttendanceRegisterRequestBuilderTest.builder().withRequestInfo().addGoodRegister().build();
        Object mdmsResponse = AttendeeRequestBuilderTest.getMdmsResponseForInvalidTenant();
        lenient().when(mdmsUtils.mDMSCall(any(RequestInfo.class),
                any(String.class))).thenReturn(mdmsResponse);
        CustomException exception = assertThrows(CustomException.class,()->attendanceServiceValidator.validateCreateAttendanceRegister(attendanceRegisterRequest));
        assertTrue(exception.getMessage().contains("INVALID_TENANT"));
    }
}
