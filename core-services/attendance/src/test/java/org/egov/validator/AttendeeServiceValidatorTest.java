package org.egov.validator;


import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.helper.AttendanceRegisterBuilderTest;
import org.egov.helper.AttendeeRequestBuilderTest;
import org.egov.tracer.model.CustomException;
import org.egov.util.MDMSUtils;
import org.egov.web.models.AttendanceRegister;
import org.egov.web.models.AttendeeCreateRequest;
import org.egov.web.models.IndividualEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class AttendeeServiceValidatorTest {

    @InjectMocks
    private AttendeeServiceValidator attendeeServiceValidator;

    @Mock
    private MDMSUtils mdmsUtils;


    @BeforeEach
    void setupBeforeEach() {
        Object mdmsResponse = AttendeeRequestBuilderTest.getMdmsResponseForValidTenant();
        lenient().when(mdmsUtils.mDMSCall(any(RequestInfo.class),
                any(String.class))).thenReturn(mdmsResponse);
    }

    @DisplayName("attendees is null in attendee request")
    @Test
    void shouldThrowExceptionWhenAttendeesIsNull_InAttendeeRequest() {

        AttendeeCreateRequest attendeeCreateRequest = AttendeeRequestBuilderTest.getAttendeeCreateRequest();
        attendeeCreateRequest.setAttendees(null);

        assertThrows(CustomException.class, () -> attendeeServiceValidator.validateAttendeeCreateRequestParameters(attendeeCreateRequest));
    }

    @DisplayName("register id is null in attendee request")
    @Test
    void shouldThrowExceptionWhenRegisterIdIsNull_InAttendeeRequest() {

        IndividualEntry attendee = IndividualEntry.builder().tenantId("pb.amritsar").id("047dc725-3088-45b4-877a-6bfbaf377df9")
                .individualId("8ybdd-3rdh3").registerId("").enrollmentDate(new BigDecimal("1672129633890"))
                .denrollmentDate(new BigDecimal("1676073600000")).build();

        AttendeeCreateRequest attendeeCreateRequest = AttendeeRequestBuilderTest.getAttendeeCreateRequest();
        attendeeCreateRequest.setAttendees(Collections.singletonList(attendee));

        assertThrows(CustomException.class, () -> attendeeServiceValidator.validateAttendeeCreateRequestParameters(attendeeCreateRequest));
    }

    @DisplayName("individual id is null in attendee request")
    @Test
    void shouldThrowExceptionWhenIndividualIdIsNull_InAttendeeRequest() {

        IndividualEntry attendee = IndividualEntry.builder().tenantId("pb.amritsar").id("047dc725-3088-45b4-877a-6bfbaf377df9")
                .individualId("").registerId("97ed7da3-753e-426a-b0b0-95dd61029785").enrollmentDate(new BigDecimal("1672129633890"))
                .denrollmentDate(new BigDecimal("1676073600000")).build();

        AttendeeCreateRequest attendeeCreateRequest = AttendeeRequestBuilderTest.getAttendeeCreateRequest();
        attendeeCreateRequest.setAttendees(Collections.singletonList(attendee));

        assertThrows(CustomException.class, () -> attendeeServiceValidator.validateAttendeeCreateRequestParameters(attendeeCreateRequest));
    }

    @DisplayName("tenantId is null in attendee request")
    @Test
    void shouldThrowExceptionWhenTenantIdIsNull_InAttendeeRequest() {

        IndividualEntry attendee = IndividualEntry.builder().tenantId("").id("047dc725-3088-45b4-877a-6bfbaf377df9")
                .individualId("8ybdd-3rdh3").registerId("97ed7da3-753e-426a-b0b0-95dd61029785").enrollmentDate(new BigDecimal("1672129633890"))
                .denrollmentDate(new BigDecimal("1676073600000")).build();

        AttendeeCreateRequest attendeeCreateRequest = AttendeeRequestBuilderTest.getAttendeeCreateRequest();
        attendeeCreateRequest.setAttendees(Collections.singletonList(attendee));

        assertThrows(CustomException.class, () -> attendeeServiceValidator.validateAttendeeCreateRequestParameters(attendeeCreateRequest));
    }

    @DisplayName("tenantId is same for all attendees in the attendee request")
    @Test
    void shouldThrowExceptionWhenTenantIdsAreNotSame_InAttendeeRequest() {

        AttendeeCreateRequest attendeeCreateRequest = AttendeeRequestBuilderTest.getAttendeeCreateRequest();
        attendeeCreateRequest.getAttendees().get(0).setTenantId("od");

        assertThrows(CustomException.class, () -> attendeeServiceValidator.validateAttendeeCreateRequestParameters(attendeeCreateRequest));
    }

    @DisplayName("verify tenantId with mdms when tenantId is present in mdms")
    @Test
    void shouldNotThrowExceptionWhenTenantIdPresent_InMDMS() {

        AttendeeCreateRequest attendeeCreateRequest = AttendeeRequestBuilderTest.getAttendeeCreateRequest();

        assertDoesNotThrow(() -> attendeeServiceValidator.validateMDMSAndRequestInfoForCreateAttendee(attendeeCreateRequest));
    }

    @DisplayName("verify tenantId with mdms when tenantId is not present in mdms")
    @Test
    void shouldThrowExceptionWhenTenantIdNotPresent_InMDMS() {

        AttendeeCreateRequest attendeeCreateRequest = AttendeeRequestBuilderTest.getAttendeeCreateRequest();
        attendeeCreateRequest.getAttendees().get(0).setTenantId("od.odisha");

        Object mdmsResponse = AttendeeRequestBuilderTest.getMdmsResponseForInvalidTenant();
        when(mdmsUtils.mDMSCall(any(RequestInfo.class),
                any(String.class))).thenReturn(mdmsResponse);

        assertThrows(CustomException.class, () -> attendeeServiceValidator.validateMDMSAndRequestInfoForCreateAttendee(attendeeCreateRequest));
    }

    //tests for create attendee validation

    @DisplayName("attendee cannot be added to register if register's end date has passed")
    @Test
    void shouldThrowExceptionWhenRegisterEndDateHasPassed() {

        AttendeeCreateRequest attendeeCreateRequest = AttendeeRequestBuilderTest.getAttendeeCreateRequest();
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegister();
        List<IndividualEntry> attendees = attendanceRegister.getAttendees();

        attendanceRegister.setEndDate(new BigDecimal("1578728218000")); //set a past date

        assertThrows(CustomException.class, () -> attendeeServiceValidator
                .validateAttendeeOnCreate(attendeeCreateRequest, attendees, Collections.singletonList(attendanceRegister)));
    }

    @DisplayName("attendee enrollment date should be after start date and before end date of register")
    @Test
    void shouldThrowExceptionWhenEnrollmentDateIsBeforeStartDateOfRegister() {

        AttendeeCreateRequest attendeeCreateRequest = AttendeeRequestBuilderTest.getAttendeeCreateRequest();
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegister();
        List<IndividualEntry> attendees = attendanceRegister.getAttendees();

        attendeeCreateRequest.getAttendees().get(0).setEnrollmentDate(new BigDecimal("1673422618000"));

        assertThrows(CustomException.class, () -> attendeeServiceValidator
                .validateAttendeeOnCreate(attendeeCreateRequest, attendees, Collections.singletonList(attendanceRegister)));
    }

    @DisplayName("check if attendee is already enrolled to the register")
    @Test
    void shouldThrowExceptionWhenAttendeeAlreadyEnrolledToRegister() {

        AttendeeCreateRequest attendeeCreateRequest = AttendeeRequestBuilderTest.getAttendeeCreateRequest();
        AttendanceRegister attendanceRegister = AttendanceRegisterBuilderTest.getAttendanceRegister();
        List<IndividualEntry> attendees = attendanceRegister.getAttendees();

        assertThrows(CustomException.class, () -> attendeeServiceValidator
                .validateAttendeeOnCreate(attendeeCreateRequest, attendees, Collections.singletonList(attendanceRegister)));

    }

}
