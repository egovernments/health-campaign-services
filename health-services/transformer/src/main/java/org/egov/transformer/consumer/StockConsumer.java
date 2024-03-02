package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.handler.TransformationHandler;
import org.egov.transformer.service.StockReconciliationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class StockConsumer {

    private final ObjectMapper objectMapper;

    private final StockReconciliationService stockReconciliationService;
    private final TransformationHandler<Stock> transformationHandler;

    public StockConsumer(ObjectMapper objectMapper, StockReconciliationService stockReconciliationService, TransformationHandler<Stock> transformationHandler) {
        this.objectMapper = objectMapper;
        this.stockReconciliationService = stockReconciliationService;
        this.transformationHandler = transformationHandler;
    }

    @KafkaListener(topics = { "${transformer.consumer.bulk.create.stock.topic}",
            "${transformer.consumer.bulk.update.stock.topic}"})
    public void consumeStock(ConsumerRecord<String, Object> payload,
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<Stock> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            Stock[].class));
            transformationHandler.handle(payloadList, Operation.STOCK);
        } catch (Exception exception) {
            log.error("error in stock consumer bulk create {}", ExceptionUtils.getStackTrace(exception));
        }
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
            log.error("error in stock consumer bulk create {}", ExceptionUtils.getStackTrace(exception));
        }
    }

}
