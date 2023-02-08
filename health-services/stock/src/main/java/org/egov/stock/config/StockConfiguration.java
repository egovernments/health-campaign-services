package org.egov.stock.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Component
public class StockConfiguration {

    @Value("${stock.kafka.create.topic}")
    private String createStockTopic;

    @Value("${stock.consumer.bulk.create.topic}")
    private String bulkCreateStockTopic;

    @Value("${stock.kafka.update.topic}")
    private String updateStockTopic;

    @Value("${stock.consumer.bulk.update.topic}")
    private String bulkUpdateStockTopic;

    @Value("${stock.kafka.delete.topic}")
    private String deleteStockTopic;

    @Value("${stock.consumer.bulk.delete.topic}")
    private String bulkDeleteStockTopic;
    
}
