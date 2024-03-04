package org.egov.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.config.AttendanceServiceConfiguration;
import org.egov.helper.AttendanceLogRequestTestBuilder;
import org.egov.helper.AttendanceRegisterBuilderTest;
import org.egov.repository.AttendeeRepository;
import org.egov.repository.RegisterRepository;
import org.egov.repository.StaffRepository;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.AttendanceLog;
import org.egov.web.models.AttendanceLogRequest;
import org.egov.web.models.AttendanceRegister;
import org.egov.web.models.AttendeeSearchCriteria;
import org.egov.web.models.IndividualEntry;
import org.egov.web.models.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class AttendanceLogServiceValidatorTest {

    @InjectMocks
    private AttendanceLogServiceValidator attendanceLogServiceValidator;

    @Mock
    private AttendanceServiceConfiguration config;

    @Mock
    private StaffRepository attendanceStaffRepository;

    @Mock
    private RegisterRepository attendanceRegisterRepository;

    @Mock
    private AttendeeRepository attendanceAttendeeRepository;

    @DisplayName("Method validateAttendanceLogRequest: With good request")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogRequest_1(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        assertDoesNotThrow(()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogRequest", attendanceLogRequest));
    }

    @DisplayName("Method validateAttendanceLogRequest: With null RequestInfo object")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogRequest_2(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withoutRequestInfo().addGoodAttendanceLog().build();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogRequest", attendanceLogRequest));
        assertTrue(exception.getCode().equals("REQUEST_INFO"));
    }

    @DisplayName("Method validateAttendanceLogRequest: RequestInfo object with null UserInfo")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogRequest_3(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfoButWithoutUserInfo().addGoodAttendanceLog().build();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogRequest", attendanceLogRequest));
        assertTrue(exception.getCode().equals("USERINFO"));
    }

    @DisplayName("Method validateAttendanceLogRequest: RequestInfo object with UserInfo but without UUID")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogRequest_4(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfoWithUserInfoButWithOutUUID().addGoodAttendanceLog().build();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogRequest", attendanceLogRequest));
        assertTrue(exception.getCode().equals("USERINFO_UUID"));
    }

    @DisplayName("Method validateAttendanceLogRequest: RequestInfo object with UserInfo but without UUID")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogRequest_5(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfoWithUserInfoButWithOutUUID().addGoodAttendanceLog().build();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogRequest", attendanceLogRequest));
        assertTrue(exception.getCode().equals("USERINFO_UUID"));
    }

    @DisplayName("Method validateAttendanceLogRequest: RequestInfo object with UserInfo but without Attendance Log list")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogRequest_6(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().withoutAttendanceLog().build();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogRequest", attendanceLogRequest));
        assertTrue(exception.getCode().equals("ATTENDANCE"));
    }

    @DisplayName("Method validateAttendanceLogRequest: RequestInfo object with UserInfo but without Attendance Log TenantId")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogRequest_7(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().attendanceLogWithoutTenantId().build();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogRequest", attendanceLogRequest));
        assertTrue(exception.toString().contains("TenantId is mandatory"));
    }

    @DisplayName("Method validateAttendanceLogRequest: RequestInfo object with UserInfo but without Attendance Log RegisterId")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogRequest_8(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().attendanceLogWithoutRegisterId().build();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogRequest", attendanceLogRequest));
        log.info(exception.toString());
        assertTrue(exception.toString().contains("Attendance registerid is mandatory"));
    }

    @DisplayName("Method validateAttendanceLogRequest: RequestInfo object with UserInfo but without Attendance Log IndividualId")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogRequest_9(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().attendanceLogWithoutIndividualId().build();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogRequest", attendanceLogRequest));
        assertTrue(exception.toString().contains("Attendance indidualid is mandatory"));
    }

    @DisplayName("Method validateAttendanceLogRequest: RequestInfo object with UserInfo but without Attendance Log Type")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogRequest_10(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().attendanceLogWithoutType().build();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogRequest", attendanceLogRequest));
        assertTrue(exception.toString().contains("Attendance type is mandatory"));
    }

    @DisplayName("Method validateAttendanceLogRequest: RequestInfo object with UserInfo but without Attendance Log Time")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogRequest_11(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().attendanceLogWithoutTime().build();;
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogRequest", attendanceLogRequest));
        assertTrue(exception.toString().contains("Attendance time is mandatory"));
    }


    @DisplayName("Method validateLoggedInUser: should throw exception with code INTEGRATION_UNDERDEVELOPMENT")
    @Test
    public void validateCreateAttendanceLogRequest_validateLoggedInUser_1(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        when(config.getStaffServiceIntegrationRequired()).thenReturn("TRUE");

        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateLoggedInUser", attendanceLogRequest));
        assertTrue(exception.getCode().equals("INTEGRATION_UNDERDEVELOPMENT"));
    }

//    @DisplayName("Method validateLoggedInUser: should through exception with error code UNAUTHORISED_USER")
//    @Test
//    public void validateCreateAttendanceLogRequest_validateLoggedInUser_2(){
//        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
//        when(config.getStaffServiceIntegrationRequired()).thenReturn("FALSE");
//        when(attendanceStaffRepository.getActiveStaff(any(StaffSearchCriteria.class))).thenReturn(null);
//
//        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateLoggedInUser", attendanceLogRequest));
//        assertTrue(exception.getCode().equals("UNAUTHORISED_USER"));
//
//    }

//    @DisplayName("Method validateLoggedInUser: should through exception with error code UNAUTHORISED_USER")
//    @Test
//    public void validateCreateAttendanceLogRequest_validateLoggedInUser_3(){
//        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
//        when(config.getStaffServiceIntegrationRequired()).thenReturn("FALSE");
//        List<StaffPermission> attendanceStaff = new ArrayList<>();
//        when(attendanceStaffRepository.getActiveStaff(any(StaffSearchCriteria.class))).thenReturn(attendanceStaff);
//
//        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateLoggedInUser", attendanceLogRequest));
//        assertTrue(exception.getCode().equals("UNAUTHORISED_USER"));
//
//    }

//    @DisplayName("Method validateLoggedInUser: should run successfully")
//    @Test
//    public void validateCreateAttendanceLogRequest_validateLoggedInUser_4(){
//        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
//        when(config.getStaffServiceIntegrationRequired()).thenReturn("FALSE");
//        StaffPermission staff = StaffPermission.builder()
//                                        .id("staff-uuid")
//                                        .tenantId("tenantId")
//                                        .userId("staffId")
//                                        .registerId("registerId")
//                                        .enrollmentDate(BigDecimal.valueOf(1L))
//                                        .denrollmentDate(BigDecimal.valueOf(1L))
//                                        .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
//                                        .additionalDetails(AdditionalFields.builder().build())
//                                        .build();
//        List<StaffPermission> attendanceStaff = new ArrayList<>();
//        attendanceStaff.add(staff);
//        when(attendanceStaffRepository.getActiveStaff(any(StaffSearchCriteria.class))).thenReturn(attendanceStaff);
//
//        assertDoesNotThrow(()->ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateLoggedInUser", attendanceLogRequest));
//
//    }

    @DisplayName("Method validateTenantIdAssociationWithRegisterId: should through exception with error code INVALID_TENANTID")
    @Test
    public void validateCreateAttendanceLogRequest_validateTenantIdAssociationWithRegisterId_1(){
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegister();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateTenantIdAssociationWithRegisterId", attendanceRegister,"other.tenantId"));
        assertTrue(exception.getCode().equals("INVALID_TENANTID"));
    }
    @DisplayName("Method validateTenantIdAssociationWithRegisterId: should run successfully")
    @Test
    public void validateCreateAttendanceLogRequest_validateTenantIdAssociationWithRegisterId_2(){
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegister();
        assertDoesNotThrow(()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateTenantIdAssociationWithRegisterId", attendanceRegister,"pb.amritsar"));
 }

    @DisplayName("Method validateAttendees: should through exception with error code INTEGRATION_UNDERDEVELOPMENT")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendees_1(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        when(config.getIndividualServiceIntegrationRequired()).thenReturn("TRUE");

        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendees", attendanceLogRequest));
        assertTrue(exception.getCode().equals("INTEGRATION_UNDERDEVELOPMENT"));
    }

    @DisplayName("Method validateAttendees: should run successfully")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendees_2(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        when(config.getIndividualServiceIntegrationRequired()).thenReturn("FALSE");

        IndividualEntry individual = IndividualEntry.builder()
                                        .registerId("some-registerId")
                                        .id("uuid")
                                        .individualId("some-individualId")
                                        .enrollmentDate(BigDecimal.valueOf(1640995200000L))
                                        .denrollmentDate(BigDecimal.valueOf(1703980800000L))
                                        .build();
        List<IndividualEntry> individualEntries = new ArrayList<>();
        individualEntries.add(individual);
        when(attendanceAttendeeRepository.getAttendees(any(AttendeeSearchCriteria.class))).thenReturn(individualEntries);
        assertDoesNotThrow( ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendees", attendanceLogRequest));
    }

    @DisplayName("Method validateAttendees: should run successfully")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendees_3(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        when(config.getIndividualServiceIntegrationRequired()).thenReturn("FALSE");

        IndividualEntry individual = IndividualEntry.builder()
                .registerId("some-registerId")
                .id("uuid")
                .individualId("some-individualId")
                .enrollmentDate(BigDecimal.valueOf(1640995200000L))
                .build();
        List<IndividualEntry> individualEntries = new ArrayList<>();
        individualEntries.add(individual);
        when(attendanceAttendeeRepository.getAttendees(any(AttendeeSearchCriteria.class))).thenReturn(individualEntries);
        assertDoesNotThrow( ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendees", attendanceLogRequest));
    }

    @DisplayName("Method validateAttendees: should run successfully")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendees_4(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        when(config.getIndividualServiceIntegrationRequired()).thenReturn("FALSE");

        IndividualEntry individual = IndividualEntry.builder()
                .registerId("some-registerId")
                .id("uuid")
                .individualId("some-individualId")
                .enrollmentDate(BigDecimal.valueOf(1672813896627L))
                .build();
        List<IndividualEntry> individualEntries = new ArrayList<>();
        individualEntries.add(individual);
        when(attendanceAttendeeRepository.getAttendees(any(AttendeeSearchCriteria.class))).thenReturn(individualEntries);
        assertDoesNotThrow( ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendees", attendanceLogRequest));
    }

    @DisplayName("Method validateAttendees: should through exception with error code INELIGIBLE_ATTENDEES")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendees_5(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        when(config.getIndividualServiceIntegrationRequired()).thenReturn("FALSE");

        IndividualEntry individual = IndividualEntry.builder()
                .registerId("some-registerId")
                .id("uuid")
                .individualId("some-individualId")
                .enrollmentDate(BigDecimal.valueOf(1705491134000L))
                .build();
        List<IndividualEntry> individualEntries = new ArrayList<>();
        individualEntries.add(individual);
        when(attendanceAttendeeRepository.getAttendees(any(AttendeeSearchCriteria.class))).thenReturn(individualEntries);
        CustomException exception = assertThrows(CustomException.class, ( ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendees", attendanceLogRequest)));
        assertTrue(exception.getCode().equals("INELIGIBLE_ATTENDEES"));
    }

    @DisplayName("Method validateAttendees: should through exception with error code INELIGIBLE_ATTENDEES")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendees_6(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        when(config.getIndividualServiceIntegrationRequired()).thenReturn("FALSE");

        IndividualEntry individual = IndividualEntry.builder()
                .registerId("some-registerId")
                .id("uuid")
                .individualId("some-individualId")
                .enrollmentDate(BigDecimal.valueOf(1579260734000L))
                .denrollmentDate(BigDecimal.valueOf(1589715134000L))
                .build();
        List<IndividualEntry> individualEntries = new ArrayList<>();
        individualEntries.add(individual);
        when(attendanceAttendeeRepository.getAttendees(any(AttendeeSearchCriteria.class))).thenReturn(individualEntries);
        CustomException exception = assertThrows(CustomException.class, ( ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendees", attendanceLogRequest)));
        assertTrue(exception.getCode().equals("INELIGIBLE_ATTENDEES"));
    }

    @DisplayName("Method validateDocumentIds: should through exception with error code SERVICE_UNAVAILABLE")
    @Test
    public void validateCreateAttendanceLogRequest_validateDocumentIds_1(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        when(config.getDocumentIdVerificationRequired()).thenReturn("TRUE");
        CustomException exception = assertThrows(CustomException.class, ( ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateDocumentIds", attendanceLogRequest)));
        assertTrue(exception.getCode().equals("SERVICE_UNAVAILABLE"));
    }

    @DisplayName("Method validateDocumentIds: should run successfully")
    @Test
    public void validateCreateAttendanceLogRequest_validateDocumentIds_2(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        when(config.getDocumentIdVerificationRequired()).thenReturn("FALSE");
        assertDoesNotThrow( ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateDocumentIds", attendanceLogRequest));
    }

    @DisplayName("Method validateAttendanceLogTimeWithRegisterStartEndDate: should run successfully")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogTimeWithRegisterStartEndDate_1(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        AttendanceLog attendanceLog = attendanceLogRequest.getAttendance().get(0);
        attendanceLog.setTime(new BigDecimal("1673740800000"));
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegister();
        assertDoesNotThrow( ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogTimeWithRegisterStartEndDate",attendanceRegister, attendanceLogRequest));
    }

    @DisplayName("Method validateAttendanceLogTimeWithRegisterStartEndDate: should through exception with error code INVALID_ATTENDANCE_TIME")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogTimeWithRegisterStartEndDate_2(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        AttendanceLog attendanceLog = attendanceLogRequest.getAttendance().get(0);
        attendanceLog.setTime(new BigDecimal("1573740800000"));
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegister();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogTimeWithRegisterStartEndDate",attendanceRegister, attendanceLogRequest));
        assertTrue(exception.getCode().equals("INVALID_ATTENDANCE_TIME"));
    }

    @DisplayName("Method validateAttendanceLogTimeWithRegisterStartEndDate: should through exception with error code INVALID_ATTENDANCE_TIME")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogTimeWithRegisterStartEndDate_3(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        AttendanceLog attendanceLog = attendanceLogRequest.getAttendance().get(0);
        attendanceLog.setTime(new BigDecimal("1792057600000"));
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegister();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogTimeWithRegisterStartEndDate",attendanceRegister, attendanceLogRequest));
        assertTrue(exception.getCode().equals("INVALID_ATTENDANCE_TIME"));
    }

    @DisplayName("Method validateAttendanceLogTimeWithRegisterStartEndDate: should run successfully")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogTimeWithRegisterStartEndDate_4(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        AttendanceLog attendanceLog = attendanceLogRequest.getAttendance().get(0);
        attendanceLog.setTime(new BigDecimal("1673740800000"));
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegister();
        assertDoesNotThrow(()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogTimeWithRegisterStartEndDate",attendanceRegister, attendanceLogRequest));
    }

    @DisplayName("Method validateAttendanceLogTimeWithRegisterStartEndDate: should run successfully")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogTimeWithRegisterStartEndDate_5(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        AttendanceLog attendanceLog = attendanceLogRequest.getAttendance().get(0);
        attendanceLog.setTime(new BigDecimal("1692057600000"));
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegister();
        assertDoesNotThrow(()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogTimeWithRegisterStartEndDate",attendanceRegister, attendanceLogRequest));
    }

    @DisplayName("Method validateAttendanceLogTimeWithRegisterStartEndDate: should run successfully")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogTimeWithRegisterStartEndDate_6(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        AttendanceLog attendanceLog = attendanceLogRequest.getAttendance().get(0);
        attendanceLog.setTime(new BigDecimal("1673740800000"));
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegisterWithoutEndDate();
        assertDoesNotThrow(()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogTimeWithRegisterStartEndDate",attendanceRegister, attendanceLogRequest));
    }

    @DisplayName("Method validateAttendanceLogTimeWithRegisterStartEndDate: should run successfully")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogTimeWithRegisterStartEndDate_7(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        AttendanceLog attendanceLog = attendanceLogRequest.getAttendance().get(0);
        attendanceLog.setTime(new BigDecimal("1773740800000"));
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegisterWithoutEndDate();
        assertDoesNotThrow(()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogTimeWithRegisterStartEndDate",attendanceRegister, attendanceLogRequest));
    }

    @DisplayName("Method validateAttendanceLogTimeWithRegisterStartEndDate: should run successfully")
    @Test
    public void validateCreateAttendanceLogRequest_validateAttendanceLogTimeWithRegisterStartEndDate_8(){
        AttendanceLogRequest attendanceLogRequest = AttendanceLogRequestTestBuilder.builder().withRequestInfo().addGoodAttendanceLog().build();
        AttendanceLog attendanceLog = attendanceLogRequest.getAttendance().get(0);
        attendanceLog.setTime(new BigDecimal("1573740800000"));
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegisterWithoutEndDate();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "validateAttendanceLogTimeWithRegisterStartEndDate",attendanceRegister, attendanceLogRequest));
        assertTrue(exception.getCode().equals("INVALID_ATTENDANCE_TIME"));
    }
    @DisplayName("Method checkRegisterStatus: should run successfully")
    @Test
    public void validateCreateAttendanceLogRequest_checkRegisterStatus_1(){
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegister();
        attendanceRegister.setStatus(Status.ACTIVE);
        assertDoesNotThrow( ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "checkRegisterStatus",attendanceRegister));
    }
    @DisplayName("Method checkRegisterStatus: should through exception with error code INACTIVE_REGISTER")
    @Test
    public void validateCreateAttendanceLogRequest_checkRegisterStatus_2(){
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegister();
        attendanceRegister.setStatus(Status.INACTIVE);
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "checkRegisterStatus",attendanceRegister));
        assertTrue(exception.getCode().equals("INACTIVE_REGISTER"));
    }

    @DisplayName("Method checkRegisterExistence: should run successfully")
    @Test
    public void validateCreateAttendanceLogRequest_checkRegisterExistence_1(){
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegister();
        List<AttendanceRegister> attendanceRegisters = Collections.singletonList(attendanceRegister);
        assertDoesNotThrow( ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "checkRegisterExistence",attendanceRegisters,"TestRegisterId"));
    }

    @DisplayName("Method checkRegisterExistence: should through exception with error code REGISTER_NOT_FOUND")
    @Test
    public void validateCreateAttendanceLogRequest_checkRegisterExistence_2(){
        List<AttendanceRegister> attendanceRegisters = null;
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "checkRegisterExistence",attendanceRegisters,"TestRegisterId"));
        assertTrue(exception.getCode().equals("REGISTER_NOT_FOUND"));
    }

    @DisplayName("Method checkRegisterExistence: should through exception with error code REGISTER_NOT_FOUND")
    @Test
    public void validateCreateAttendanceLogRequest_checkRegisterExistence_3(){
        List<AttendanceRegister> attendanceRegisters = new ArrayList<>();
        CustomException exception = assertThrows(CustomException.class, ()-> ReflectionTestUtils.invokeMethod(attendanceLogServiceValidator, "checkRegisterExistence",attendanceRegisters,"TestRegisterId"));
        assertTrue(exception.getCode().equals("REGISTER_NOT_FOUND"));
    }
}
