package org.egov.referralmanagement.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.referralmanagement.service.ReferralManagementService;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
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
public class ReferralManagementConsumer {

    private final ReferralManagementService referralManagementService;

    private final ObjectMapper objectMapper;

    @Autowired
    public ReferralManagementConsumer(ReferralManagementService referralManagementService,
                                      @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.referralManagementService = referralManagementService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${referralmanagement.referral.consumer.bulk.create.topic}")
    public void bulkCreate(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ReferralBulkRequest request = objectMapper.convertValue(consumerRecord, ReferralBulkRequest.class);
            referralManagementService.create(request, true);
        } catch (Exception exception) {
            log.error("Error in Referral consumer bulk create", exception);
            log.error("Exception trace: {}", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_REFERRAL_MANAGEMENT_REFERRAL_CREATE", exception.getMessage());
        }
    }

    @KafkaListener(topics = "${referralmanagement.referral.consumer.bulk.update.topic}")
    public void bulkUpdate(Map<String, Object> consumerRecord,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ReferralBulkRequest request = objectMapper.convertValue(consumerRecord, ReferralBulkRequest.class);
            referralManagementService.update(request, true);
        } catch (Exception exception) {
            log.error("Error in Referral consumer bulk update", exception);
            log.error("Exception trace: {}", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_REFERRAL_MANAGEMENT_REFERRAL_UPDATE", exception.getMessage());
        }
    }

    @KafkaListener(topics = "${referralmanagement.referral.consumer.bulk.delete.topic}")
    public void bulkDelete(Map<String, Object> consumerRecord,
                                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            ReferralBulkRequest request = objectMapper.convertValue(consumerRecord, ReferralBulkRequest.class);
            referralManagementService.delete(request, true);
        } catch (Exception exception) {
            log.error("Error in Referral consumer bulk delete", exception);
            log.error("Exception trace: {}", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("HCM_REFERRAL_MANAGEMENT_REFERRAL_DELETE", exception.getMessage());
        }
    }
}
