package org.egov.helper;

import org.egov.web.models.AttendanceLog;
import org.egov.web.models.AttendanceLogRequest;

import java.util.ArrayList;

public class AttendanceLogRequestTestBuilder {

    private AttendanceLogRequest.AttendanceLogRequestBuilder builder ;

    public AttendanceLogRequestTestBuilder(){
        this.builder = AttendanceLogRequest.builder();
    }

    public static AttendanceLogRequestTestBuilder builder(){
        return new AttendanceLogRequestTestBuilder();
    }

    public AttendanceLogRequest build(){
        return this.builder.build();
    }

    public AttendanceLogRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }

    public AttendanceLogRequestTestBuilder withoutRequestInfo(){
        this.builder.requestInfo(null);
        return this;
    }

    public AttendanceLogRequestTestBuilder withRequestInfoButWithoutUserInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().requestInfoWithoutUserInfo().build());
        return this;
    }

    public AttendanceLogRequestTestBuilder withRequestInfoWithUserInfoButWithOutUUID(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().requestInfoWithUserInfoButWithOutUUID().build());
        return this;
    }

    public AttendanceLogRequestTestBuilder addGoodAttendanceLog(){
        ArrayList<AttendanceLog> logs = new ArrayList<>();
        logs.add(AttendanceLogTestBuilder.builder().addGoodAttendanceLog().build());
        this.builder.attendance(logs);
        return this;
    }

    public AttendanceLogRequestTestBuilder addAttendanceLogWithoutIdAndAuditDetails(){
        ArrayList<AttendanceLog> logs = new ArrayList<>();
        logs.add(AttendanceLogTestBuilder.builder().addAttendanceLogWithoutIdAndAuditDetails().build());
        this.builder.attendance(logs);
        return this;
    }

    public AttendanceLogRequestTestBuilder withoutAttendanceLog(){
        this.builder.attendance(null);
        return this;
    }

    public AttendanceLogRequestTestBuilder attendanceLogWithoutTenantId(){
        ArrayList<AttendanceLog> logs = new ArrayList<>();
        logs.add(AttendanceLogTestBuilder.builder().attendanceLogWithoutTenantId().build());
        this.builder.attendance(logs);
        return this;
    }


    public AttendanceLogRequestTestBuilder attendanceLogWithoutIndividualId(){
        ArrayList<AttendanceLog> logs = new ArrayList<>();
        logs.add(AttendanceLogTestBuilder.builder().attendanceLogWithoutIndividualId().build());
        this.builder.attendance(logs);
        return this;
    }

    public AttendanceLogRequestTestBuilder attendanceLogWithoutType(){
        ArrayList<AttendanceLog> logs = new ArrayList<>();
        logs.add(AttendanceLogTestBuilder.builder().attendanceLogWithoutType().build());
        this.builder.attendance(logs);
        return this;
    }

    public AttendanceLogRequestTestBuilder attendanceLogWithoutTime(){
        ArrayList<AttendanceLog> logs = new ArrayList<>();
        logs.add(AttendanceLogTestBuilder.builder().attendanceLogWithoutTime().build());
        this.builder.attendance(logs);
        return this;
    }

    public AttendanceLogRequestTestBuilder attendanceLogWithoutRegisterId() {
        ArrayList<AttendanceLog> logs = new ArrayList<>();
        logs.add(AttendanceLogTestBuilder.builder().attendanceLogWithoutRegisterId().build());
        this.builder.attendance(logs);
        return this;
    }
}
