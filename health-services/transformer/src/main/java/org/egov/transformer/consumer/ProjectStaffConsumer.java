package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.project.ProjectStaff;
import org.egov.transformer.transformationservice.ProjectStaffTransformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class ProjectStaffConsumer {


    private final ObjectMapper objectMapper;
    private final ProjectStaffTransformationService projectStaffTransformationService;

    @Autowired
    public ProjectStaffConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, ProjectStaffTransformationService projectStaffTransformationService) {
        this.objectMapper = objectMapper;
        this.projectStaffTransformationService = projectStaffTransformationService;
    }

    @KafkaListener(topics = {"${transformer.consumer.bulk.create.project.staff.topic}",
            "${transformer.consumer.bulk.update.project.staff.topic}"})
    public void consumeStaff(ConsumerRecord<String, Object> payload,
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<ProjectStaff> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            ProjectStaff[].class));
            projectStaffTransformationService.transform(payloadList);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in projectStaff consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}
