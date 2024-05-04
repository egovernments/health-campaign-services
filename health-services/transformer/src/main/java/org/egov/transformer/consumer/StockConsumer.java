package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.common.models.stock.Stock;
import org.egov.transformer.transformationservice.StockTransformationService;
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

    private final StockTransformationService stockTransformationService;

    public StockConsumer(ObjectMapper objectMapper, StockTransformationService stockTransformationService) {
        this.objectMapper = objectMapper;
        this.stockTransformationService = stockTransformationService;
    }

    @KafkaListener(topics = {"${transformer.consumer.bulk.create.stock.topic}",
            "${transformer.consumer.bulk.update.stock.topic}"})
    public void consumeStock(ConsumerRecord<String, Object> payload,
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<Stock> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            Stock[].class));
            stockTransformationService.transform(payloadList);
        } catch (Exception exception) {
            log.error("TRANSFORMER error in stockConsumer {}", ExceptionUtils.getStackTrace(exception));
        }
    }
}
