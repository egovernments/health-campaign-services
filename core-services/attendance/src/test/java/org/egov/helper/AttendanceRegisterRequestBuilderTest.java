package org.egov.helper;

import org.egov.web.models.AttendanceRegister;
import org.egov.web.models.AttendanceRegisterRequest;
import org.egov.web.models.IndividualEntry;
import org.egov.web.models.StaffPermission;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;

public class AttendanceRegisterRequestBuilderTest {

    private AttendanceRegisterRequest.AttendanceRegisterRequestBuilder builder ;

    public AttendanceRegisterRequestBuilderTest(){
        this.builder = AttendanceRegisterRequest.builder();
    }

    public static AttendanceRegisterRequestBuilderTest builder(){
        return new AttendanceRegisterRequestBuilderTest();
    }

    public AttendanceRegisterRequest build(){
        return this.builder.build();
    }

    public AttendanceRegisterRequestBuilderTest withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }

    public AttendanceRegisterRequestBuilderTest requestInfoWithoutUserInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().requestInfoWithoutUserInfo().build());
        return this;
    }

    public AttendanceRegisterRequestBuilderTest requestInfoWithUserInfoButWithOutUUID(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().requestInfoWithUserInfoButWithOutUUID().build());
        return this;
    }

    public AttendanceRegisterRequestBuilderTest addGoodRegister(){
        ArrayList<AttendanceRegister> registers = new ArrayList<>();
        AttendanceRegister attendanceRegister=AttendanceRegister.builder()
                .id("97ed7da3-753e-426a-b0b0-95dd61029785")
                .registerNumber("RGN-67124")
                .tenantId("pb.amritsar")
                .name("self help3")
                .startDate(new BigDecimal("1673740800000"))
                .endDate(new BigDecimal("1692057600000"))
                .serviceCode("serviceCode")
                .referenceId("referenceId")
                .auditDetails(AuditDetailsTestBuilder.builder().build())
                .attendees(Collections.singletonList(IndividualEntry.builder().build()))
                .staff(Collections.singletonList(StaffPermission.builder().build()))
                .build();
        registers.add(attendanceRegister);
        this.builder.attendanceRegister(registers);
        return this;
    }


    public AttendanceRegisterRequestBuilderTest attendanceRegistersWithoutIdAuditDetailsAndNumber(){
        ArrayList<AttendanceRegister> registers = new ArrayList<>();
        AttendanceRegister attendanceRegister=AttendanceRegister.builder()
                .tenantId("pb.amritsar")
                .name("self help3")
                .startDate(new BigDecimal("1673740800000"))
                .endDate(new BigDecimal("1692057600000"))
                .auditDetails(null)
                .attendees(Collections.singletonList(IndividualEntry.builder().build()))
                .staff(Collections.singletonList(StaffPermission.builder().build()))
                .build();
        registers.add(attendanceRegister);
        this.builder.attendanceRegister(registers);
        return this;
    }

    public AttendanceRegisterRequestBuilderTest attendanceRegistersWithMultipleTenantIds(){
        ArrayList<AttendanceRegister> registers = new ArrayList<>();
        AttendanceRegister attendanceRegister1=AttendanceRegister.builder()
                .id("97ed7da3-753e-426a-b0b0-95dd61029785")
                .registerNumber("RGN-67124")
                .tenantId("pb.amritsar")
                .name("self help3")
                .startDate(new BigDecimal("1673740800000"))
                .endDate(new BigDecimal("1692057600000"))
                .auditDetails(AuditDetailsTestBuilder.builder().build())
                .attendees(Collections.singletonList(IndividualEntry.builder().build()))
                .staff(Collections.singletonList(StaffPermission.builder().build()))
                .build();

        AttendanceRegister attendanceRegister2=AttendanceRegister.builder()
                .id("97ed7da3-753e-426a-b0b0-95dd61029785")
                .registerNumber("RGN-67124")
                .tenantId("pb.jalandhar")
                .name("self help3")
                .startDate(new BigDecimal("1673740800000"))
                .endDate(new BigDecimal("1692057600000"))
                .auditDetails(AuditDetailsTestBuilder.builder().build())
                .attendees(Collections.singletonList(IndividualEntry.builder().build()))
                .staff(Collections.singletonList(StaffPermission.builder().build()))
                .build();

        registers.add(attendanceRegister1);
        registers.add(attendanceRegister2);
        this.builder.attendanceRegister(registers);
        return this;
    }

    public AttendanceRegisterRequestBuilderTest attendanceRegisterWithoutTenantId(){
        ArrayList<AttendanceRegister> registers = new ArrayList<>();
        AttendanceRegister attendanceRegister=AttendanceRegister.builder()
                                                    .id("97ed7da3-753e-426a-b0b0-95dd61029785")
                                                    .registerNumber("RGN-67124")
                                                    .name("self help3")
                                                    .startDate(new BigDecimal("1673740800000"))
                                                    .endDate(new BigDecimal("1692057600000"))
                                                    .auditDetails(AuditDetailsTestBuilder.builder().build())
                                                    .attendees(Collections.singletonList(IndividualEntry.builder().build()))
                                                    .staff(Collections.singletonList(StaffPermission.builder().build()))
                                                    .build();
        registers.add(attendanceRegister);
        this.builder.attendanceRegister(registers);
        return this;
    }

    public AttendanceRegisterRequestBuilderTest attendanceRegisterWithoutName(){
        ArrayList<AttendanceRegister> registers = new ArrayList<>();
        AttendanceRegister attendanceRegister=AttendanceRegister.builder()
                .id("97ed7da3-753e-426a-b0b0-95dd61029785")
                .registerNumber("RGN-67124")
                .tenantId("tenant.id")
                .startDate(new BigDecimal("1673740800000"))
                .endDate(new BigDecimal("1692057600000"))
                .auditDetails(AuditDetailsTestBuilder.builder().build())
                .attendees(Collections.singletonList(IndividualEntry.builder().build()))
                .staff(Collections.singletonList(StaffPermission.builder().build()))
                .build();
        registers.add(attendanceRegister);
        this.builder.attendanceRegister(registers);
        return this;
    }

    public AttendanceRegisterRequestBuilderTest attendanceRegisterWithoutStartDate(){
        ArrayList<AttendanceRegister> registers = new ArrayList<>();
        AttendanceRegister attendanceRegister=AttendanceRegister.builder()
                .id("97ed7da3-753e-426a-b0b0-95dd61029785")
                .registerNumber("RGN-67124")
                .name("some-name")
                .tenantId("tenant.id")
                .endDate(new BigDecimal("1692057600000"))
                .auditDetails(AuditDetailsTestBuilder.builder().build())
                .attendees(Collections.singletonList(IndividualEntry.builder().build()))
                .staff(Collections.singletonList(StaffPermission.builder().build()))
                .build();
        registers.add(attendanceRegister);
        this.builder.attendanceRegister(registers);
        return this;
    }

    public AttendanceRegisterRequestBuilderTest attendanceRegisterWithStartDateGTEndDate(){
        ArrayList<AttendanceRegister> registers = new ArrayList<>();
        AttendanceRegister attendanceRegister=AttendanceRegister.builder()
                .id("97ed7da3-753e-426a-b0b0-95dd61029785")
                .registerNumber("RGN-67124")
                .name("some-name")
                .tenantId("tenant.id")
                .startDate(new BigDecimal("1692057600000"))
                .endDate(new BigDecimal("1673740800000"))
                .auditDetails(AuditDetailsTestBuilder.builder().build())
                .attendees(Collections.singletonList(IndividualEntry.builder().build()))
                .staff(Collections.singletonList(StaffPermission.builder().build()))
                .build();
        registers.add(attendanceRegister);
        this.builder.attendanceRegister(registers);
        return this;
    }

}
