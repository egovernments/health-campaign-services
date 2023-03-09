package org.egov.transformer.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.handler.StockTransformationHandler;
import org.egov.transformer.handler.TransformationHandler;
import org.egov.transformer.models.upstream.Stock;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class StockConsumer {

    private final ObjectMapper objectMapper;

    private final TransformationHandler<StockTransformationHandler> transformationHandler;

    public StockConsumer(ObjectMapper objectMapper, TransformationHandler<StockTransformationHandler> transformationHandler) {
        this.objectMapper = objectMapper;
        this.transformationHandler = transformationHandler;
    }

    public void bulkCreate(ConsumerRecord<String, Object> payload,
                           @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<Stock> payloadList = Arrays.asList(objectMapper
                    .readValue((String) payload.value(),
                            Stock[].class));
            transformationHandler.handle(payloadList, Operation.TASK);
        } catch (Exception exception) {
            log.error("error in project task consumer bulk create", exception);
        }
    }
}
