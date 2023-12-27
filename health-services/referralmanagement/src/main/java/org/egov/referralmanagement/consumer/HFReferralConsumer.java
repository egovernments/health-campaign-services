package org.egov.referralmanagement.consumer;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.referralmanagement.service.HFReferralService;
import org.egov.referralmanagement.service.ReferralManagementService;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HFReferralConsumer {

    private final HFReferralService hfReferralService;

    private final ObjectMapper objectMapper;

    @Autowired
    public HFReferralConsumer(HFReferralService hfReferralService,
                              @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.hfReferralService = hfReferralService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${referralmanagement.hfreferral.consumer.bulk.create.topic}")
    public void bulkCreate(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            HFReferralBulkRequest request = objectMapper.convertValue(consumerRecord, HFReferralBulkRequest.class);
            hfReferralService.create(request, true);
        } catch (Exception exception) {
            log.error("Error in HFReferral consumer bulk create", exception);
            log.error("Exception trace: ", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_REFERRAL_MANAGEMENT_REFERRAL_CREATE", exception.getMessage());
        }
    }

    @KafkaListener(topics = "${referralmanagement.hfreferral.consumer.bulk.update.topic}")
    public void bulkUpdate(Map<String, Object> consumerRecord,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            HFReferralBulkRequest request = objectMapper.convertValue(consumerRecord, HFReferralBulkRequest.class);
            hfReferralService.update(request, true);
        } catch (Exception exception) {
            log.error("Error in HFReferral consumer bulk update", exception);
            log.error("Exception trace: ", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_REFERRAL_MANAGEMENT_REFERRAL_UPDATE", exception.getMessage());
        }
    }

    @KafkaListener(topics = "${referralmanagement.hfreferral.consumer.bulk.delete.topic}")
    public void bulkDelete(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            HFReferralBulkRequest request = objectMapper.convertValue(consumerRecord, HFReferralBulkRequest.class);
            hfReferralService.delete(request, true);
        } catch (Exception exception) {
            log.error("Error in HFReferral consumer bulk delete", exception);
            log.error("Exception trace: ", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_REFERRAL_MANAGEMENT_REFERRAL_DELETE", exception.getMessage());
        }
    }
}
