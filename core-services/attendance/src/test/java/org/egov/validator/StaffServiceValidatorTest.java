package org.egov.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.helper.AttendanceRegisterBuilderTest;
import org.egov.helper.AttendeeRequestBuilderTest;
import org.egov.helper.StaffRequestBuilderTest;
import org.egov.service.AttendanceRegisterService;
import org.egov.tracer.model.CustomException;
import org.egov.util.MDMSUtils;
import org.egov.web.models.AttendanceRegister;
import org.egov.web.models.StaffPermission;
import org.egov.web.models.StaffPermissionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class StaffServiceValidatorTest {

    @Mock
    private MDMSUtils mdmsUtils;

    @Mock
    private AttendanceRegisterService attendanceRegisterService;

    @InjectMocks
    private StaffServiceValidator staffServiceValidator;

    @BeforeEach
    void setupBeforeEach() {
        Object mdmsResponse = AttendeeRequestBuilderTest.getMdmsResponseForValidTenant();
        lenient().when(mdmsUtils.mDMSCall(any(RequestInfo.class),
                any(String.class))).thenReturn(mdmsResponse);
    }


    //validate staff request parameters
    @DisplayName("staff is null in staff Permission request")
    @Test
    void shouldThrowExceptionWhenStaffIsNull_InStaffPermissionRequest() {

        StaffPermissionRequest staffPermissionRequest = StaffRequestBuilderTest.getStaffPermissionRequest();
        staffPermissionRequest.setStaff(null);

        assertThrows(CustomException.class, () -> staffServiceValidator.validateStaffPermissionRequestParameters(staffPermissionRequest));
    }

    @DisplayName("register id is null in staff Permission request")
    @Test
    void shouldThrowExceptionWhenRegisterIdIsNull_InStaffPermissionRequest() {

        StaffPermissionRequest staffPermissionRequest = StaffRequestBuilderTest.getStaffPermissionRequest();
        staffPermissionRequest.getStaff().get(0).setRegisterId(null);

        assertThrows(CustomException.class, () -> staffServiceValidator.validateStaffPermissionRequestParameters(staffPermissionRequest));
    }

    @DisplayName("tenant id is null in staff Permission request")
    @Test
    void shouldThrowExceptionWhenTenantIdIsNull_InStaffPermissionRequest() {

        StaffPermissionRequest staffPermissionRequest = StaffRequestBuilderTest.getStaffPermissionRequest();
        staffPermissionRequest.getStaff().get(0).setTenantId(null);

        assertThrows(CustomException.class, () -> staffServiceValidator.validateStaffPermissionRequestParameters(staffPermissionRequest));
    }

    @DisplayName("All staff must have same tenant id in staff Permission request")
    @Test
    void shouldThrowExceptionWhenTenantIdIsNotSame_InStaffPermissionRequest() {

        StaffPermissionRequest staffPermissionRequest = StaffRequestBuilderTest.getStaffPermissionRequest();
        staffPermissionRequest.getStaff().get(0).setTenantId("od");

        assertThrows(CustomException.class, () -> staffServiceValidator.validateStaffPermissionRequestParameters(staffPermissionRequest));
    }

    @DisplayName("Duplicate staff objects in staff Permission request")
    @Test
    void shouldThrowExceptionWhenDuplicateStaffIsPresent_InStaffPermissionRequest() {

        StaffPermission staffOne = StaffPermission.builder().id("03901adb-07c3-4539-9346-4ee5c87e5e1c").userId("8ybdd-3rdhd")
                .registerId("97ed7da3-753e-426a-b0b0-95dd61029785").tenantId("pb.amritsar").enrollmentDate(new BigDecimal("1670421853937"))
                .denrollmentDate(null).build();

        StaffPermissionRequest staffPermissionRequest = StaffRequestBuilderTest.getStaffPermissionRequest();
        staffPermissionRequest.getStaff().set(1, staffOne);

        assertThrows(CustomException.class, () -> staffServiceValidator.validateStaffPermissionRequestParameters(staffPermissionRequest));
    }


    //validate tenant id with mdms
    @DisplayName("verify tenantId with mdms when tenantId is present in mdms")
    @Test
    void shouldNotThrowExceptionWhenTenantIdPresent_InMDMS() {

        StaffPermissionRequest staffPermissionRequest = StaffRequestBuilderTest.getStaffPermissionRequest();

        assertDoesNotThrow(() -> staffServiceValidator.validateMDMSAndRequestInfoForStaff(staffPermissionRequest));
    }

    @DisplayName("verify tenantId with mdms when tenantId is not present in mdms")
    @Test
    void shouldThrowExceptionWhenTenantIdNotPresent_InMDMS() {

        StaffPermissionRequest staffPermissionRequest = StaffRequestBuilderTest.getStaffPermissionRequest();
        staffPermissionRequest.getStaff().get(0).setTenantId("od.odisha");

        Object mdmsResponse = AttendeeRequestBuilderTest.getMdmsResponseForInvalidTenant();
        when(mdmsUtils.mDMSCall(any(RequestInfo.class),
                any(String.class))).thenReturn(mdmsResponse);

        assertThrows(CustomException.class, () -> staffServiceValidator.validateMDMSAndRequestInfoForStaff(staffPermissionRequest));
    }

    //check if staff tenant id is same as register tenant id
    @DisplayName("check if staff tenant id is same as register tenant id")
    @Test
    void shouldThrowExceptionWhenStaffTenantIdNotSameAsRegisterTenantId() {

        StaffPermissionRequest staffPermissionRequest = StaffRequestBuilderTest.getStaffPermissionRequest();
        List<StaffPermission> staffPermissionList = AttendanceRegisterBuilderTest.getStaff();
        List<AttendanceRegister> attendanceRegisterList = AttendanceRegisterBuilderTest.getAttendanceRegisterList();

        attendanceRegisterList.get(0).setTenantId("od.odisha");

        assertThrows(CustomException.class, () -> staffServiceValidator.validateStaffPermissionOnCreate(staffPermissionRequest, staffPermissionList, attendanceRegisterList));
    }

    @DisplayName("check if register end date has passed")
    @Test
    void shouldThrowExceptionIfRegisterEndDateHasPassed() {

        StaffPermissionRequest staffPermissionRequest = StaffRequestBuilderTest.getStaffPermissionRequest();
        List<StaffPermission> staffPermissionList = AttendanceRegisterBuilderTest.getStaff();
        List<AttendanceRegister> attendanceRegisterList = AttendanceRegisterBuilderTest.getAttendanceRegisterList();

        attendanceRegisterList.get(0).setEndDate(new BigDecimal("1547733538000"));

        assertThrows(CustomException.class, () -> staffServiceValidator.validateStaffPermissionOnCreate(staffPermissionRequest, staffPermissionList, attendanceRegisterList));
    }

    @DisplayName("check if staff is already enrolled to the given register")
    @Test
    void shouldThrowExceptionIfStaffIsAlreadyEnrolledToTheRegister() {

        StaffPermissionRequest staffPermissionRequest = StaffRequestBuilderTest.getStaffPermissionRequest();
        List<StaffPermission> staffPermissionList = AttendanceRegisterBuilderTest.getStaff();
        List<AttendanceRegister> attendanceRegisterList = AttendanceRegisterBuilderTest.getAttendanceRegisterList();


        assertThrows(CustomException.class, () -> staffServiceValidator.validateStaffPermissionOnCreate(staffPermissionRequest, staffPermissionList, attendanceRegisterList));
    }


}
