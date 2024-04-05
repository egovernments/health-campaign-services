package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.transformer.service.StockReconciliationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class StockReconciliationConsumer {

    private final ObjectMapper objectMapper;

    private final StockReconciliationService stockReconciliationService;

    public StockReconciliationConsumer(ObjectMapper objectMapper, StockReconciliationService stockReconciliationService) {
        this.objectMapper = objectMapper;
        this.stockReconciliationService = stockReconciliationService;
    }

    @KafkaListener(topics = { "${transformer.consumer.stock.reconciliation.create.topic}",
            "${transformer.consumer.stock.reconciliation.update.topic}"})
    public void consumeStockReconciliation(ConsumerRecord<String, Object> payload,
                                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<StockReconciliation> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            StockReconciliation[].class));
            stockReconciliationService.transform(payloadList);
        } catch (Exception exception) {
            log.error("error in stock reconciliation consumer bulk create {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}
