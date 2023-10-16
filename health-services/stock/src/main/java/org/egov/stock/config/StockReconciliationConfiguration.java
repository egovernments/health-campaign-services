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
public class StockReconciliationConfiguration {

    @Value("${stock.reconciliation.kafka.create.topic}")
    private String createStockReconciliationTopic;

    @Value("${stock.reconciliation.consumer.bulk.create.topic}")
    private String bulkCreateStockReconciliationTopic;

    @Value("${stock.reconciliation.kafka.update.topic}")
    private String updateStockReconciliationTopic;

    @Value("${stock.reconciliation.consumer.bulk.update.topic}")
    private String bulkUpdateStockReconciliationTopic;

    @Value("${stock.reconciliation.kafka.delete.topic}")
    private String deleteStockReconciliationTopic;

    @Value("${stock.reconciliation.consumer.bulk.delete.topic}")
    private String bulkDeleteStockReconciliationTopic;

    @Value("${egov.product.host}")
    private String productHost;

    @Value("${egov.search.product.variant.url}")
    private String productVariantSearchUrl;

    @Value("${stock.reconciliation.idgen.id.format}")
    private String stockReconciliationIdFormat;

    @Value("${egov.persister.bulk.processing.enabled}")
    private Boolean isPersisterBulkProcessingEnabled;
}
