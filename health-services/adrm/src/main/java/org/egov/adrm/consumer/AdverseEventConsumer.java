package org.egov.adrm.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.adrm.adverseevent.AdverseEventBulkRequest;
import org.egov.adrm.service.AdverseEventService;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

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

    @KafkaListener(topics = "${adrm.adverseevent.consumer.bulk.create.topic}")
    public void bulkCreate(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            AdverseEventBulkRequest request = objectMapper.convertValue(consumerRecord, AdverseEventBulkRequest.class);
            adverseEventService.create(request, true);
        } catch (Exception exception) {
            log.error("Error in Adverse Event consumer bulk create", exception);
            log.error("Exception trace: ", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_PROJECT_ADVERSE_EVENT_CREATE", exception.getMessage());
        }
    }

    @KafkaListener(topics = "${adrm.adverseevent.consumer.bulk.update.topic}")
    public void bulkUpdate(Map<String, Object> consumerRecord,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            AdverseEventBulkRequest request = objectMapper.convertValue(consumerRecord, AdverseEventBulkRequest.class);
            adverseEventService.update(request, true);
        } catch (Exception exception) {
            log.error("Error in Adverse Event consumer bulk update", exception);
            log.error("Exception trace: ", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_PROJECT_ADVERSE_EVENT_CREATE", exception.getMessage());
        }
    }

    @KafkaListener(topics = "${adrm.adverseevent.consumer.bulk.delete.topic}")
    public void bulkDelete(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            AdverseEventBulkRequest request = objectMapper.convertValue(consumerRecord, AdverseEventBulkRequest.class);
            adverseEventService.delete(request, true);
        } catch (Exception exception) {
            log.error("Error in Adverse Event consumer bulk delete", exception);
            log.error("Exception trace: ", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_PROJECT_ADVERSE_EVENT_CREATE", exception.getMessage());
        }
    }
}
