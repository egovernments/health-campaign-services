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
        this.userActionService = userActionService;
        this.locationCaptureService = locationCaptureService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${project.user.action.task.consumer.bulk.create.topic}")
    public List<UserAction> bulkCreateUserAction(Map<String, Object> consumerRecord,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            UserActionBulkRequest request = objectMapper.convertValue(consumerRecord, UserActionBulkRequest.class);
            return userActionService.create(request, true);
        }  catch (Exception exception) {
            log.error("error in user action consumer bulk create", ExceptionUtils.getStackTrace(exception));
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.user.action.task.consumer.bulk.update.topic}")
    public List<UserAction> bulkUpdateUserAction(Map<String, Object> consumerRecord,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            UserActionBulkRequest request = objectMapper.convertValue(consumerRecord, UserActionBulkRequest.class);
            return userActionService.update(request, true);
        } catch (Exception exception) {
            log.error("error in user action consumer bulk update", ExceptionUtils.getStackTrace(exception));
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${project.location.capture.task.consumer.bulk.create.topic}")
    public List<UserAction> bulkCreateLocationCapture(Map<String, Object> consumerRecord,
                                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            UserActionBulkRequest request = objectMapper.convertValue(consumerRecord, UserActionBulkRequest.class);
            return locationCaptureService.create(request, true);
        } catch (Exception exception) {
            log.error("error in locatin capture consumer bulk create", ExceptionUtils.getStackTrace(exception));
            return Collections.emptyList();
        }
    }

}
