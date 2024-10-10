package org.egov.product.config;

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
public class ProductConfiguration {
    @Value("${product.kafka.create.topic}")
    private String createProductTopic;

    @Value("${product.kafka.update.topic}")
    private String updateProductTopic;

    @Value("${product.variant.kafka.create.topic}")
    private String createProductVariantTopic;

    @Value("${product.variant.kafka.update.topic}")
    private String updateProductVariantTopic;

    @Value("${egov.mdms.host}")
    private String mdmsHost;

    @Value("${egov.mdms.search.endpoint}")
    private String mdmsEndPoint;
}
