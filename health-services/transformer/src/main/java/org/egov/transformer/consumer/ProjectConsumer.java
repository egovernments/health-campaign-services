package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.project.ProjectRequest;
import org.egov.transformer.transformationservice.ProjectTransformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProjectConsumer {
    private final ObjectMapper objectMapper;

    private final ProjectTransformationService projectTransformationService;

    @Autowired
    public ProjectConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, ProjectTransformationService projectTransformationService) {
        this.objectMapper = objectMapper;
        this.projectTransformationService = projectTransformationService;
    }

    @KafkaListener(topics = {"${transformer.consumer.create.project.topic}",
            "${transformer.consumer.update.project.topic}"})
    public void consumeProjects(ConsumerRecord<String, Object> payload,
                                @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ProjectRequest request = objectMapper
                    .readValue((String) payload.value(),
                            ProjectRequest.class);
            projectTransformationService.transform(request.getProjects());
        } catch (Exception exception) {
            log.error("TRANSFORMER error in project consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}
