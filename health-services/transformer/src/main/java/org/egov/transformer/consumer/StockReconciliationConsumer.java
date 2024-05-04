package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.transformer.transformationservice.StockReconciliationTransformationService;
import org.springframework.beans.factory.annotation.Qualifier;
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

    private final StockReconciliationTransformationService stockReconciliationTransformationService;

    public StockReconciliationConsumer(@Qualifier("objectMapper") ObjectMapper objectMapper, StockReconciliationTransformationService stockReconciliationTransformationService) {
        this.objectMapper = objectMapper;
        this.stockReconciliationTransformationService = stockReconciliationTransformationService;
    }

    @KafkaListener(topics = {"${transformer.consumer.stock.reconciliation.create.topic}",
            "${transformer.consumer.stock.reconciliation.update.topic}"})
    public void consumeStockReconciliation(ConsumerRecord<String, Object> payload,
                                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<StockReconciliation> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            StockReconciliation[].class));
            stockReconciliationTransformationService.transform(payloadList);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in stockReconciliationConsumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}
