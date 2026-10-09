package org.egov.referralmanagement.consumer;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.referralmanagement.service.HFReferralService;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer for handling HFReferral-related messages.
 * Author: kanishq-egov
 */
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

    /**
     * Kafka listener method to handle bulk creation of HFReferrals.
     * Author: kanishq-egov
     *
     * @param consumerRecord The Kafka message payload.
     * @param topic          The Kafka topic from which the message is received.
     */
    @KafkaListener(topics = "${referralmanagement.hfreferral.consumer.bulk.create.topic}")
    public void bulkCreate(Map<String, Object> consumerRecord,
                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            // Convert the Kafka message payload to HFReferralBulkRequest
            HFReferralBulkRequest request = objectMapper.convertValue(consumerRecord, HFReferralBulkRequest.class);

            // Invoke the HFReferralService to handle bulk creation
            hfReferralService.create(request, true);
        } catch (Exception exception) {
            log.error("Error in HFReferral consumer bulk create", exception);
            log.error("Exception trace: ", ExceptionUtils.getStackTrace(exception));

            // Throw a CustomException in case of an error during bulk creation
            throw new CustomException("HCM_REFERRAL_MANAGEMENT_REFERRAL_CREATE", exception.getMessage());
        }
    }

    /**
     * Kafka listener method to handle bulk update of HFReferrals.
     * Author: kanishq-egov
     *
     * @param consumerRecord The Kafka message payload.
     * @param topic          The Kafka topic from which the message is received.
     */
    @KafkaListener(topics = "${referralmanagement.hfreferral.consumer.bulk.update.topic}")
    public void bulkUpdate(Map<String, Object> consumerRecord,
                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            // Convert the Kafka message payload to HFReferralBulkRequest
            HFReferralBulkRequest request = objectMapper.convertValue(consumerRecord, HFReferralBulkRequest.class);

            // Invoke the HFReferralService to handle bulk update
            hfReferralService.update(request, true);
        } catch (Exception exception) {
            log.error("Error in HFReferral consumer bulk update", exception);
            log.error("Exception trace: ", ExceptionUtils.getStackTrace(exception));

            // Throw a CustomException in case of an error during bulk update
            throw new CustomException("HCM_REFERRAL_MANAGEMENT_REFERRAL_UPDATE", exception.getMessage());
        }
    }

    /**
     * Kafka listener method to handle bulk deletion of HFReferrals.
     * Author: kanishq-egov
     *
     * @param consumerRecord The Kafka message payload.
     * @param topic          The Kafka topic from which the message is received.
     */
    @KafkaListener(topics = "${referralmanagement.hfreferral.consumer.bulk.delete.topic}")
    public void bulkDelete(Map<String, Object> consumerRecord,
                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            // Convert the Kafka message payload to HFReferralBulkRequest
            HFReferralBulkRequest request = objectMapper.convertValue(consumerRecord, HFReferralBulkRequest.class);

            // Invoke the HFReferralService to handle bulk deletion
            hfReferralService.delete(request, true);
        } catch (Exception exception) {
            log.error("Error in HFReferral consumer bulk delete", exception);
            log.error("Exception trace: ", ExceptionUtils.getStackTrace(exception));

            // Throw a CustomException in case of an error during bulk deletion
            throw new CustomException("HCM_REFERRAL_MANAGEMENT_REFERRAL_DELETE", exception.getMessage());
        }
    }
}
