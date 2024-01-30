package org.egov.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.egov.tracer.config.TracerConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

@Import({TracerConfiguration.class})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Component
public class AttendanceLogConfiguration {

    @Value("${attendance.log.kafka.consumer.bulk.create.topic}")
    private String createAttendanceLogBulkTopic;

    @Value("${attendance.log.kafka.consumer.bulk.update.topic}")
    private String updateAttendanceLogBulkTopic;

}
