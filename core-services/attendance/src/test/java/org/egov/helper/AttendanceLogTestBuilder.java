package org.egov.helper;

import org.egov.web.models.AttendanceLog;
import org.egov.web.models.Status;

import java.math.BigDecimal;
import java.util.Collections;

public class AttendanceLogTestBuilder {
    private AttendanceLog.AttendanceLogBuilder builder ;
    public AttendanceLogTestBuilder(){
        this.builder = AttendanceLog.builder();
    }

    public static AttendanceLogTestBuilder builder(){
        return new AttendanceLogTestBuilder();
    }

    public AttendanceLog build(){
        return this.builder.build();
    }

    public AttendanceLogTestBuilder addGoodAttendanceLog(){
        this.builder
                .id("some-id")
                .tenantId("some-tenantId")
                .registerId("some-registerId")
                .individualId("some-individualId")
                .time(BigDecimal.valueOf(1672813896627L))
                .type("some-type")
                .status(Status.ACTIVE)
                .documentIds(Collections.singletonList(DocumentTestBuilder.builder().addGoodDocument().build()))
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
                ;
        return this;
    }

    public AttendanceLogTestBuilder addAttendanceLogWithoutIdAndAuditDetails(){
        this.builder
                .tenantId("some-tenantId")
                .registerId("some-registerId")
                .individualId("some-individualId")
                .time(BigDecimal.valueOf(1672813896627L))
                .type("some-type")
                .status(Status.ACTIVE)
                .documentIds(Collections.singletonList(DocumentTestBuilder.builder().addDocumentWithoutId().build()))
                .auditDetails(null)
        ;
        return this;
    }

    public AttendanceLogTestBuilder attendanceLogWithoutTenantId(){
        this.builder
                .id("some-id")
                .registerId("some-registerId")
                .individualId("some-individualId")
                .time(BigDecimal.valueOf(1L))
                .type("some-type")
                .status(Status.ACTIVE)
                .documentIds(Collections.singletonList(DocumentTestBuilder.builder().addGoodDocument().build()))
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
        ;
        return this;
    }

    public AttendanceLogTestBuilder attendanceLogWithoutRegisterId(){
        this.builder
                .id("some-id")
                .tenantId("some-tenantId")
                .individualId("some-individualId")
                .time(BigDecimal.valueOf(1L))
                .type("some-type")
                .status(Status.ACTIVE)
                .documentIds(Collections.singletonList(DocumentTestBuilder.builder().addGoodDocument().build()))
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
        ;
        return this;
    }

    public AttendanceLogTestBuilder attendanceLogWithoutIndividualId(){
        this.builder
                .id("some-id")
                .tenantId("some-tenantId")
                .registerId("some-registerId")
                .time(BigDecimal.valueOf(1L))
                .type("some-type")
                .status(Status.ACTIVE)
                .documentIds(Collections.singletonList(DocumentTestBuilder.builder().addGoodDocument().build()))
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
        ;
        return this;
    }

    public AttendanceLogTestBuilder attendanceLogWithoutType(){
        this.builder
                .id("some-id")
                .tenantId("some-tenantId")
                .registerId("some-registerId")
                .individualId("some-individualId")
                .time(BigDecimal.valueOf(1L))
                .status(Status.ACTIVE)
                .documentIds(Collections.singletonList(DocumentTestBuilder.builder().addGoodDocument().build()))
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
        ;
        return this;
    }

    public AttendanceLogTestBuilder attendanceLogWithoutTime(){
        this.builder
                .id("some-id")
                .tenantId("some-tenantId")
                .registerId("some-registerId")
                .individualId("some-individualId")
                .type("some-type")
                .status(Status.ACTIVE)
                .documentIds(Collections.singletonList(DocumentTestBuilder.builder().addGoodDocument().build()))
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
        ;
        return this;
    }


}
