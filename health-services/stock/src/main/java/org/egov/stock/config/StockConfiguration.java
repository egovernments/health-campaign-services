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

    @Value("${egov.product.host}")
    private String productHost;

    @Value("${egov.search.product.variant.url}")
    private String productVariantSearchUrl;

    @Value("${egov.mdms.host}")
    private String mdmsHost;

    @Value("${egov.mdms.search.endpoint}")
    private String mdmsSearchEndpoint;

    @Value("${stock.idgen.id.format}")
    private String stockIdFormat;

    @Value("${egov.facility.host}")
    private String facilityServiceHost;

    @Value("${egov.search.facility.url}")
    private String facilityServiceSearchUrl;

    @Value("${egov.project.facility.host}")
    private String projectFacilityServiceHost;

    @Value("${egov.search.project.facility.url}")
    private String projectFacilityServiceSearchUrl;
    
}
