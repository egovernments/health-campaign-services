package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.project.ProjectStaff;
import org.egov.transformer.producer.TransformerErrorProducer;
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
    private final TransformerErrorProducer errorQueueProducer;

    @Autowired
    public ProjectStaffConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper,
                                ProjectStaffTransformationService projectStaffTransformationService,
                                TransformerErrorProducer errorQueueProducer) {
        this.objectMapper = objectMapper;
        this.projectStaffTransformationService = projectStaffTransformationService;
        this.errorQueueProducer = errorQueueProducer;
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
            errorQueueProducer.sendToErrorTopic(payload.value(), topic, exception);
        }
    }
}
