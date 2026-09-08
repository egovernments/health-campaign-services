package org.egov.workerregistry.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.workerregistry.service.WorkerService;
import org.egov.workerregistry.web.models.AttendanceDocumentEventRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class AttendanceDocumentEventConsumer {

    private final WorkerService workerService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AttendanceDocumentEventConsumer(WorkerService workerService, ObjectMapper objectMapper) {
        this.workerService = workerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topicPattern = "(${attendance.kafka.tenant.id.pattern}){0,1}${attendance.log.kafka.first.signature.topic}")
    public void consume(Map<String, Object> consumerRecord) {
        try {
            AttendanceDocumentEventRequest request = objectMapper.convertValue(consumerRecord, AttendanceDocumentEventRequest.class);
            workerService.processAttendanceDocumentEvent(request.getEvent(), request.getRequestInfo());
        } catch (Exception e) {
            log.error("Error processing attendance document event", e);
        }
    }
}
