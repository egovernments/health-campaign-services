package org.egov.workerregistry.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.workerregistry.service.WorkerService;
import org.egov.workerregistry.web.models.PhotoSignatureUpdateRecord;
import org.egov.workerregistry.web.models.PhotoSignatureUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
@ConditionalOnProperty(name = "worker.photo.signature.update.enabled", havingValue = "true", matchIfMissing = true)
public class WorkerPhotoSignatureConsumer {

    private final WorkerService workerService;
    private final ObjectMapper objectMapper;

    @Autowired
    public WorkerPhotoSignatureConsumer(WorkerService workerService, ObjectMapper objectMapper) {
        this.workerService = workerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${hcm.worker.consumer.bulk.update.topic}")
    public void consumePhotoSignatureUpdate(Map<String, Object> consumerRecord,
                                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            PhotoSignatureUpdateRequest request = objectMapper.convertValue(consumerRecord,
                    PhotoSignatureUpdateRequest.class);
            List<PhotoSignatureUpdateRecord> records = request.getRecords();
            if (records == null || records.isEmpty()) {
                log.info("No records in photo/signature update request");
                return;
            }

            Map<String, List<PhotoSignatureUpdateRecord>> recordsByTenant = records.stream()
                    .collect(Collectors.groupingBy(PhotoSignatureUpdateRecord::getTenantId));

            for (Map.Entry<String, List<PhotoSignatureUpdateRecord>> entry : recordsByTenant.entrySet()) {
                workerService.updatePhotoSignature(entry.getValue(), entry.getKey(),
                        request.getRequestInfo());
            }
        } catch (Exception exception) {
            log.error("Error in worker photo/signature update consumer", exception);
        }
    }
}
