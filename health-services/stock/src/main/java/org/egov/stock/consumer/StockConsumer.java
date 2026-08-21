package org.egov.stock.consumer;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.stock.service.StockService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StockConsumer {

    private final StockService service;
    private final ObjectMapper objectMapper;

    public StockConsumer(StockService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${stock.consumer.bulk.create.topic}")
    public List<Stock> bulkCreate(Map<String, Object> consumerRecord,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            StockBulkRequest request = objectMapper.convertValue(consumerRecord, StockBulkRequest.class);
            return service.create(request, true);
        } catch (Exception exception) {
            log.error("error in stock consumer bulk create: {}", ExceptionUtils.getStackTrace(exception));
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${stock.consumer.bulk.update.topic}")
    public List<Stock> bulkUpdate(Map<String, Object> consumerRecord,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            StockBulkRequest request = objectMapper.convertValue(consumerRecord, StockBulkRequest.class);
            return service.update(request, true);
        } catch (Exception exception) {
            log.error("error in stock consumer bulk update: {}", ExceptionUtils.getStackTrace(exception));
            return Collections.emptyList();
        }
    }

    @KafkaListener(topics = "${stock.consumer.bulk.delete.topic}")
    public List<Stock> bulkDelete(Map<String, Object> consumerRecord,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            StockBulkRequest request = objectMapper.convertValue(consumerRecord, StockBulkRequest.class);
            return service.delete(request, true);
        } catch (Exception exception) {
            log.error("error in stock consumer bulk delete: {}", ExceptionUtils.getStackTrace(exception));
            return Collections.emptyList();
        }
    }
}
