package org.egov.helper;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.web.models.AttendanceRegister;
import org.egov.web.models.IndividualEntry;
import org.egov.web.models.StaffPermission;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class AttendanceRegisterBuilderTest {

    public static AttendanceRegister getAttendanceRegister(){
        AttendanceRegister attendanceRegister=AttendanceRegister.builder().id("97ed7da3-753e-426a-b0b0-95dd61029785")
                .registerNumber("RGN-67124").name("self help3").startDate(new BigDecimal("1673740800000"))
                .endDate(new BigDecimal("1692057600000")).auditDetails(getAuditDetails()).attendees(getAttendees())
                .tenantId("pb.amritsar").staff(getStaff()).build();
        return attendanceRegister;
    }

    public static AttendanceRegister getAttendanceRegisterWithoutEndDate(){
        AttendanceRegister attendanceRegister=AttendanceRegister.builder().id("97ed7da3-753e-426a-b0b0-95dd61029785")
                .registerNumber("RGN-67124").name("self help3").startDate(new BigDecimal("1673740800000"))
                .auditDetails(getAuditDetails()).attendees(getAttendees())
                .tenantId("pb.amritsar").staff(getStaff()).build();
        return attendanceRegister;
    }
    public static List<IndividualEntry> getAttendees() {
        IndividualEntry attendeeOne = IndividualEntry.builder().tenantId("pb.amritsar").id("047dc725-3088-45b4-877a-6bfbaf377df9")
                .individualId("8ybdd-3rdh3").registerId("97ed7da3-753e-426a-b0b0-95dd61029785").enrollmentDate(new BigDecimal("1672129633890"))
                .denrollmentDate(null).auditDetails(getAuditDetails()).build();

        IndividualEntry attendeeTwo = IndividualEntry.builder().tenantId("pb.amritsar").id("11b88488-9a7a-48da-b110-dfdc077ef467")
                .individualId("8ybdd-3rdha").registerId("97ed7da3-753e-426a-b0b0-95dd61029785").enrollmentDate(new BigDecimal("1671701038563"))
                .denrollmentDate(null).auditDetails(getAuditDetails()).build();

        List<IndividualEntry> attendees=new ArrayList<>(Arrays.asList(attendeeOne,attendeeTwo));
        return attendees;
    }

    public static List<StaffPermission> getStaff(){
        StaffPermission staffOne=StaffPermission.builder().id("03901adb-07c3-4539-9346-4ee5c87e5e1c").userId("8ybdd-3rdhd")
                .registerId("97ed7da3-753e-426a-b0b0-95dd61029785").tenantId("pb.amritsar").enrollmentDate(new BigDecimal("1670421853937"))
                .denrollmentDate(null).auditDetails(getAuditDetails()).build();

        StaffPermission staffTwo=StaffPermission.builder().id("156d07fd-2c0c-4882-be03-6b68b98fb15e").userId("8ybdd-3rdhe")
                .registerId("97ed7da3-753e-426a-b0b0-95dd61029785").tenantId("pb.amritsar").enrollmentDate(new BigDecimal("1670424209106"))
                .denrollmentDate(null).auditDetails(getAuditDetails()).build();

        List<StaffPermission> staffList=new ArrayList<>(Arrays.asList(staffOne,staffTwo));
        return staffList;
    }



    public static AuditDetails getAuditDetails(){
        AuditDetails auditDetails=AuditDetails.builder().createdBy("11b0e02b-0145-4de2-bc42-c97b96264807")
                .lastModifiedBy("11b0e02b-0145-4de2-bc42-c97b96264807").createdTime(Long.valueOf("1672129633890"))
                .lastModifiedTime(Long.valueOf("1672129855564")).build();
        return auditDetails;
    }

    public static List<AttendanceRegister> getAttendanceRegisterList(){

        AttendanceRegister attendanceRegisterOne=AttendanceRegister.builder().id("54215cb7-c7f7-4521-8965-09647454a1f0")
                .registerNumber("RGN-67124").name("self help3").startDate(new BigDecimal("1673740800000"))
                .endDate(new BigDecimal("1692057600000")).tenantId("pb.amritsar").auditDetails(getAuditDetails()).build();

        AttendanceRegister attendanceRegisterTwo=AttendanceRegister.builder().id("54215cb7-c7f7-4521-8965-09647454a1f1")
                .registerNumber("RGN-67124").name("self help3").startDate(new BigDecimal("1673740800000"))
                .endDate(new BigDecimal("1692057600000")).auditDetails(getAuditDetails()).tenantId("pb.amritsar").build();

        AttendanceRegister attendanceRegisterThree=AttendanceRegister.builder().id("54215cb7-c7f7-4521-8965-09647454a1f2")
                .registerNumber("RGN-67124").name("self help3").startDate(new BigDecimal("1673740800000"))
                .endDate(new BigDecimal("1692057600000")).auditDetails(getAuditDetails()).tenantId("pb.amritsar").build();

        List<AttendanceRegister> attendanceRegisterList=new ArrayList<>(Arrays.asList(attendanceRegisterOne,attendanceRegisterTwo,attendanceRegisterThree));

        return attendanceRegisterList;


    }

    public static RequestInfo getRequestInfo(){
        Role role = new Role(1L,"Organization staff","ORG_STAFF","pb.amritsar");
        List<Role> roles = new ArrayList<>();
        roles.add(role);
        User userInfo = User.builder().id(172L).uuid("5ce80dd3-b1c0-42fd-b8f6-a2be456db31c").userName("8070102021").name("test3").mobileNumber("8070102021")
                .emailId("xyz@egovernments.org").type("EMPLOYEE").roles(roles).build();
        RequestInfo requestInfo = RequestInfo.builder().apiId("attendance-services").msgId("search with from and to values").userInfo(userInfo).build();
        return requestInfo;
    }

    public static ResponseInfo getResponseInfo_Success() {
        ResponseInfo responseInfo = ResponseInfo.builder().apiId("attendance-services").ver(null).ts(null).resMsgId(null).msgId("search with from and to values")
                .status("successful").build();
        return responseInfo;
    }
}
