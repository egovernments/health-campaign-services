package org.egov.project.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.project.service.ProjectBeneficiaryService;
import org.egov.project.web.models.BeneficiaryBulkRequest;
import org.egov.project.web.models.ProjectBeneficiary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

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
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        BeneficiaryBulkRequest request = objectMapper.convertValue(consumerRecord, BeneficiaryBulkRequest.class);
        return projectBeneficiaryService.create(request, true);
    }

    @KafkaListener(topics = "${project.beneficiary.consumer.bulk.update.topic}")
    public List<ProjectBeneficiary> bulkUpdate(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        BeneficiaryBulkRequest request = objectMapper.convertValue(consumerRecord, BeneficiaryBulkRequest.class);
        return projectBeneficiaryService.update(request, true);
    }

    @KafkaListener(topics = "${project.beneficiary.consumer.bulk.delete.topic}")
    public List<ProjectBeneficiary> bulkDelete(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
        BeneficiaryBulkRequest request = objectMapper.convertValue(consumerRecord, BeneficiaryBulkRequest.class);
        return projectBeneficiaryService.delete(request, true);
    }
}
