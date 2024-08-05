package org.egov.project.consumer;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.common.models.project.useraction.UserActionBulkRequest;
import org.egov.project.service.LocationCaptureService;
import org.egov.project.service.UserActionService;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserActionConsumer {

    private final UserActionService userActionService;
    private final LocationCaptureService locationCaptureService;
    private final ObjectMapper objectMapper;

    @Autowired
    public UserActionConsumer(UserActionService userActionService, LocationCaptureService locationCaptureService, ObjectMapper objectMapper) {
        // Constructor injection for services and object mapper
        this.userActionService = userActionService;
        this.locationCaptureService = locationCaptureService;
        this.objectMapper = objectMapper;
    }

    /**
     * Kafka listener for bulk creating user actions.
     *
     * @param consumerRecord The Kafka consumer record as a map.
     * @param topic          The topic from which the message was received.
     * @return List of created UserAction objects.
     */
    @KafkaListener(topics = "${project.user.action.consumer.bulk.create.topic}")
    public void bulkCreateUserAction(Map<String, Object> consumerRecord,
                                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            // Convert consumer record to UserActionBulkRequest object
            UserActionBulkRequest request = objectMapper.convertValue(consumerRecord, UserActionBulkRequest.class);
            // Call the userActionService to handle the create operation
            userActionService.create(request, true);
        } catch (Exception exception) {
            // Log any exception that occurs
            log.error("error in user action consumer bulk create", ExceptionUtils.getStackTrace(exception));
            // throw custom exception
            throw new CustomException("PROJECT_USER_ACTION_BULK_CREATE", exception.getMessage());
        }
    }

    /**
     * Kafka listener for bulk updating user actions.
     *
     * @param consumerRecord The Kafka consumer record as a map.
     * @param topic          The topic from which the message was received.
     * @return List of updated UserAction objects.
     */
    @KafkaListener(topics = "${project.user.action.consumer.bulk.update.topic}")
    public void bulkUpdateUserAction(Map<String, Object> consumerRecord,
                                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            // Convert consumer record to UserActionBulkRequest object
            UserActionBulkRequest request = objectMapper.convertValue(consumerRecord, UserActionBulkRequest.class);
            // Call the userActionService to handle the update operation
            userActionService.update(request, true);
        } catch (Exception exception) {
            // Log any exception that occurs
            log.error("error in user action consumer bulk update", ExceptionUtils.getStackTrace(exception));
            // throw custom exception
            throw new CustomException("PROJECT_USER_ACTION_BULK_UPDATE", exception.getMessage());
        }
    }

    /**
     * Kafka listener for bulk creating location captures.
     *
     * @param consumerRecord The Kafka consumer record as a map.
     * @param topic          The topic from which the message was received.
     * @return List of created UserAction objects.
     */
    @KafkaListener(topics = "${project.location.capture.consumer.bulk.create.topic}")
    public void bulkCreateLocationCapture(Map<String, Object> consumerRecord,
                                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            // Convert consumer record to UserActionBulkRequest object
            UserActionBulkRequest request = objectMapper.convertValue(consumerRecord, UserActionBulkRequest.class);
            // Call the locationCaptureService to handle the create operation
            locationCaptureService.create(request, true);
        } catch (Exception exception) {
            // Log any exception that occurs
            log.error("error in location capture consumer bulk create", ExceptionUtils.getStackTrace(exception));
            // throw custom exception
            throw new CustomException("PROJECT_USER_ACTION_LOCATION_CAPTURE_BULK_CREATE", exception.getMessage());
        }
    }

}
