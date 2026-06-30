package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.models.bill.BillDetail;
import org.egov.transformer.producer.TransformerErrorProducer;
import org.egov.transformer.transformationservice.BillTransformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class BillDetailConsumer {
    private final ObjectMapper objectMapper;
    private final BillTransformationService billTransformationService;
    private final TransformerErrorProducer errorQueueProducer;

    @Autowired
    public BillDetailConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper,
                              BillTransformationService billTransformationService,
                              TransformerErrorProducer errorQueueProducer) {
        this.objectMapper = objectMapper;
        this.billTransformationService = billTransformationService;
        this.errorQueueProducer = errorQueueProducer;
    }

    @KafkaListener(topics = {"${transformer.consumer.save.billdetail.topic}",
            "${transformer.consumer.update.billdetail.topic}"})
    public void consumeBillDetails(ConsumerRecord<String, Object> payload,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            JsonNode root = objectMapper.readTree((String) payload.value());
            BillDetail billDetail = objectMapper.treeToValue(
                    root.get("billDetail"),
                    BillDetail.class
            );
            billTransformationService.transform(billDetail);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in bill detail consumer {}", ExceptionUtils.getStackTrace(exception));
            errorQueueProducer.sendToErrorTopic(payload.value(), topic, exception);
        }
    }
}
