package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.transformer.producer.TransformerErrorProducer;
import org.egov.transformer.transformationservice.ProjectBeneficiaryTransformationService;
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
public class ProjectBeneficiaryConsumer {

    private final ObjectMapper objectMapper;
    private final ProjectBeneficiaryTransformationService projectBeneficiaryTransformationService;
    private final TransformerErrorProducer errorQueueProducer;

    @Autowired
    public ProjectBeneficiaryConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper,
                                      ProjectBeneficiaryTransformationService projectBeneficiaryTransformationService,
                                      TransformerErrorProducer errorQueueProducer) {
        this.objectMapper = objectMapper;
        this.projectBeneficiaryTransformationService = projectBeneficiaryTransformationService;
        this.errorQueueProducer = errorQueueProducer;
    }

    @KafkaListener(topics = {"${transformer.consumer.project.beneficiary.create.topic}",
            "${transformer.consumer.project.beneficiary.update.topic}"})
    public void consumeBeneficiary(ConsumerRecord<String, Object> payload,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            errorQueueProducer.withSourceTopic(topic, () -> {
            List<ProjectBeneficiary> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            ProjectBeneficiary[].class));
            projectBeneficiaryTransformationService.transform(payloadList);
            });
        } catch (Exception exception) {
            log.error("TRANSFORMER error in projectBeneficiary consumer {}", ExceptionUtils.getStackTrace(exception));
            errorQueueProducer.sendToErrorTopic(payload.value(), topic, exception);
        }
    }
}
