package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.models.bill.BillDetail;
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

    @Autowired
    public BillDetailConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, BillTransformationService billTransformationService) {
        this.objectMapper = objectMapper;
        this.billTransformationService = billTransformationService;
    }

    @KafkaListener(topics = {"${transformer.consumer.save.billdetail.topic}",
            "${transformer.consumer.update.billdetail.topic}"})
    public void consumeBillDetails(ConsumerRecord<String, Object> payload,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            BillDetail billDetail = objectMapper.readValue((String) payload.value(), BillDetail.class);
            billTransformationService.transform(billDetail);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in bill detail consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}
