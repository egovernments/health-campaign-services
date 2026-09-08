package org.egov.referralmanagement.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
import org.egov.referralmanagement.service.SideEffectService;
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
public class SideEffectConsumer {

    private final SideEffectService sideEffectService;

    private final ObjectMapper objectMapper;

    @Autowired
    public SideEffectConsumer(SideEffectService sideEffectService,
                              @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.sideEffectService = sideEffectService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${referralmanagement.sideeffect.consumer.bulk.create.topic}")
    public void bulkCreate(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            SideEffectBulkRequest request = objectMapper.convertValue(consumerRecord, SideEffectBulkRequest.class);
            sideEffectService.create(request, true);
        } catch (Exception exception) {
            log.error("Error in Side Effect consumer bulk create", exception);
            log.error("Exception trace: {}", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_REFERRAL_MANAGEMENT_SIDE_EFFECT_CREATE", exception.getMessage());
        }
    }

    @KafkaListener(topics = "${referralmanagement.sideeffect.consumer.bulk.update.topic}")
    public void bulkUpdate(Map<String, Object> consumerRecord,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            SideEffectBulkRequest request = objectMapper.convertValue(consumerRecord, SideEffectBulkRequest.class);
            sideEffectService.update(request, true);
        } catch (Exception exception) {
            log.error("Error in Side Effect consumer bulk update", exception);
            log.error("Exception trace: {}", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_REFERRAL_MANAGEMENT_SIDE_EFFECT_UPDATE", exception.getMessage());
        }
    }

    @KafkaListener(topics = "${referralmanagement.sideeffect.consumer.bulk.delete.topic}")
    public void bulkDelete(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            SideEffectBulkRequest request = objectMapper.convertValue(consumerRecord, SideEffectBulkRequest.class);
            sideEffectService.delete(request, true);
        } catch (Exception exception) {
            log.error("Error in Side Effect consumer bulk delete", exception);
            log.error("Exception trace: {}", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_REFERRAL_MANAGEMENT_SIDE_EFFECT_DELETE", exception.getMessage());
        }
    }
}
