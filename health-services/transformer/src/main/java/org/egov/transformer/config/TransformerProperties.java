package org.egov.transformer.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Component
public class TransformerProperties {

    @Value("${transformer.producer.bulk.project.task.index.v1.topic}")
    private String transformerProducerBulkProjectTaskIndexV1Topic;

    @Value("${transformer.producer.bulk.project.index.v1.topic}")
    private String transformerProducerBulkProjectIndexV1Topic;

    @Value("${egov.project.host}")
    private String projectHost;

    @Value("${egov.search.project.url}")
    private String projectSearchUrl;

    @Value("${egov.location.host}")
    private String locationHost;

    @Value("${egov.location.endpoint}")
    private String locationSearchUrl;

    @Value("${search.api.limit:100}")
    private String searchApiLimit;
}
