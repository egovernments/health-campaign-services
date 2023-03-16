package org.egov.project.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.project.service.ProjectBeneficiaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ProjectBeneficiaryConsumer {

    private final ProjectBeneficiaryService projectBeneficiaryService;

    private final ObjectMapper objectMapper;

    @Autowired
    public ProjectBeneficiaryConsumer(ProjectBeneficiaryService projectBeneficiaryService,
                              @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.projectBeneficiaryService = projectBeneficiaryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${project.beneficiary.consumer.bulk.create.topic}")
    public List<ProjectBeneficiary> bulkCreate(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            BeneficiaryBulkRequest request = objectMapper.convertValue(consumerRecord, BeneficiaryBulkRequest.class);
            return projectBeneficiaryService.create(request, true);
        } catch (Exception exception) {
            log.error("error in project beneficiary consumer bulk create", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.beneficiary.consumer.bulk.update.topic}")
    public List<ProjectBeneficiary> bulkUpdate(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            BeneficiaryBulkRequest request = objectMapper.convertValue(consumerRecord, BeneficiaryBulkRequest.class);
            return projectBeneficiaryService.update(request, true);
        } catch (Exception exception) {
            log.error("error in project beneficiary consumer bulk update", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.beneficiary.consumer.bulk.delete.topic}")
    public List<ProjectBeneficiary> bulkDelete(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            BeneficiaryBulkRequest request = objectMapper.convertValue(consumerRecord, BeneficiaryBulkRequest.class);
            return projectBeneficiaryService.delete(request, true);
        } catch (Exception exception) {
            log.error("error in project beneficiary consumer bulk delete", exception);
            return Collections.emptyList();
        }
    }
}
