package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.transformer.transformationservice.ProjectBeneficiaryTransformationService;
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

    public ProjectBeneficiaryConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, ProjectBeneficiaryTransformationService projectBeneficiaryTransformationService) {
        this.objectMapper = objectMapper;
        this.projectBeneficiaryTransformationService = projectBeneficiaryTransformationService;
    }

    @KafkaListener(topics = {"${transformer.consumer.project.beneficiary.create.topic}",
            "${transformer.consumer.project.beneficiary.update.topic}"})
    public void consumeProjectBeneficiaries(ConsumerRecord<String, Object> payload,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<ProjectBeneficiary> projectBeneficiaryList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            ProjectBeneficiary[].class));
            projectBeneficiaryTransformationService.transform(projectBeneficiaryList);
        } catch (Exception exception) {
            log.error("error in project beneficiary consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}
