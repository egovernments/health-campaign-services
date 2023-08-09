package org.egov.project.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.AdverseEvent;
import org.egov.common.models.project.AdverseEventBulkRequest;
import org.egov.project.service.AdverseEventService;
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
public class AdverseEventConsumer {

    private final AdverseEventService adverseEventService;

    private final ObjectMapper objectMapper;

    @Autowired
    public AdverseEventConsumer(AdverseEventService adverseEventService,
                                      @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.adverseEventService = adverseEventService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${project.adverseevent.consumer.bulk.create.topic}")
    public List<AdverseEvent> bulkCreate(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            AdverseEventBulkRequest request = objectMapper.convertValue(consumerRecord, AdverseEventBulkRequest.class);
            return adverseEventService.create(request, true);
        } catch (Exception exception) {
            log.error("error in adverse event consumer bulk create", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.adverseevent.consumer.bulk.update.topic}")
    public List<AdverseEvent> bulkUpdate(Map<String, Object> consumerRecord,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            AdverseEventBulkRequest request = objectMapper.convertValue(consumerRecord, AdverseEventBulkRequest.class);
            return adverseEventService.update(request, true);
        } catch (Exception exception) {
            log.error("error in adverse event consumer bulk update", exception);
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.adverseevent.consumer.bulk.delete.topic}")
    public List<AdverseEvent> bulkDelete(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            AdverseEventBulkRequest request = objectMapper.convertValue(consumerRecord, AdverseEventBulkRequest.class);
            return adverseEventService.delete(request, true);
        } catch (Exception exception) {
            log.error("error in adverse event consumer bulk delete", exception);
            return Collections.emptyList();
        }
    }
}
