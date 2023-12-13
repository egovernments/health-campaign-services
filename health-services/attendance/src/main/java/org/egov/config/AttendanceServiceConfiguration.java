package org.egov.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.TimeZone;

@Component
@Data
@Import({TracerConfiguration.class})
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceServiceConfiguration {

    @Value("${app.timezone}")
    private String timeZone;
    //Idgen Config
    @Value("${egov.idgen.host}")
    private String idGenHost;
    @Value("${egov.idgen.path}")
    private String idGenPath;
    @Value("${egov.idgen.attendance.register.number.name}")
    private String idgenAttendanceRegisterNumberName;
    //MDMS
    @Value("${egov.mdms.host}")
    private String mdmsHost;
    @Value("${egov.mdms.search.endpoint}")
    private String mdmsEndPoint;
    //Topic
    @Value("${attendance.register.kafka.create.topic}")
    private String saveAttendanceRegisterTopic;
    @Value("${attendance.register.kafka.update.topic}")
    private String updateAttendanceRegisterTopic;

    //Topic
    @Value("${attendance.staff.kafka.create.topic}")
    private String saveStaffTopic;
    @Value("${attendance.staff.kafka.update.topic}")
    private String updateStaffTopic;

    //Topic
    @Value("${attendance.attendee.kafka.create.topic}")
    private String saveAttendeeTopic;
    @Value("${attendance.attendee.kafka.update.topic}")
    private String updateAttendeeTopic;

    @PostConstruct
    public void initialize() {
        TimeZone.setDefault(TimeZone.getTimeZone(timeZone));
    }

    // kafka topics
    @Value("${attendance.log.kafka.create.topic}")
    private String createAttendanceLogTopic;

    @Value("${attendance.log.kafka.update.topic}")
    private String updateAttendanceLogTopic;

    // service integration config
    @Value("${attendance.individual.service.integration.required}")
    private String individualServiceIntegrationRequired;

    @Value("${attendance.staff.service.integration.required}")
    private String staffServiceIntegrationRequired;

    @Value("${attendance.document.id.verification.required}")
    private String documentIdVerificationRequired;

    //attendance service log search config

    //@Value("${attendance.service.log.default.offset}")
    //private Integer attendanceLogDefaultOffset;

    //@Value("${attendance.service.log.default.limit}")
    //private Integer attendanceLogDefaultLimit;

    //@Value("${attendance.service.log.search.max.limit}")
    //private Integer attendanceLogMaxLimit;

    //attendance service register search config
    @Value("${attendance.register.default.offset}")
    private Integer attendanceRegisterDefaultOffset;

    @Value("${attendance.register.default.limit}")
    private Integer attendanceRegisterDefaultLimit;

    @Value("${attendance.register.search.max.limit}")
    private Integer attendanceRegisterMaxLimit;

    @Value("${attendance.register.open.search.enabled.roles}")
    private String registerOpenSearchEnabledRoles;

    //Individual servcie
    @Value("${works.individual.host}")
    private String individualHost;
    @Value("${works.individual.search.endpoint}")
    private String individualSearchEndpoint;

}


