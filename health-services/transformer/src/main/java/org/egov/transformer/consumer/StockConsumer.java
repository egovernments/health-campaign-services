package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.stock.Stock;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.handler.TransformationHandler;
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

    private final TransformationHandler<Stock> transformationHandler;

    public StockConsumer(ObjectMapper objectMapper, TransformationHandler<Stock> transformationHandler) {
        this.objectMapper = objectMapper;
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
            log.error("error in stock consumer bulk create", exception);
        }
    }
}
