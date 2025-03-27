package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.models.expense.Bill;
import org.egov.transformer.models.expense.BillDetail;
import org.egov.transformer.models.expense.BillRequest;
import org.egov.transformer.transformationservice.ExpenseBillTransformationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExpenseBillConsumer {

    private final ObjectMapper objectMapper;
    private final ExpenseBillTransformationService expenseBillTransformationService;

    public ExpenseBillConsumer(ObjectMapper objectMapper, ExpenseBillTransformationService expenseBillTransformationService) {
        this.objectMapper = objectMapper;
        this.expenseBillTransformationService = expenseBillTransformationService;
    }

    @KafkaListener(topics = {"${transformer.consumer.save.expense.bill.topic}",
            "${transformer.consumer.update.expense.bill.topic}"})
    public void expenseBillConsumer(ConsumerRecord<String, Object> payload,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic){
        try {
            BillRequest billRequest = objectMapper.readValue((String) payload.value(), BillRequest.class);
            Bill bill = billRequest.getBill();
            expenseBillTransformationService.transform(bill);
        } catch (Exception exception) {
            log.error("error in expense bill roll consumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}
