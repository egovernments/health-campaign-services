package org.egov.adrm.config;

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
public class AdrmConfiguration {
    @Value("${adrm.adverseevent.kafka.create.topic}")
    private String createAdverseEventTopic;

    @Value("${adrm.adverseevent.kafka.update.topic}")
    private String updateAdverseEventTopic;

    @Value("${adrm.adverseevent.kafka.delete.topic}")
    private String deleteAdverseEventTopic;

    @Value("${adrm.adverseevent.consumer.bulk.create.topic}")
    private String createAdverseEventBulkTopic;

    @Value("${adrm.adverseevent.consumer.bulk.update.topic}")
    private String updateAdverseEventBulkTopic;

    @Value("${adrm.adverseevent.consumer.bulk.delete.topic}")
    private String deleteAdverseEventBulkTopic;

    @Value("${egov.project.host}")
    private String projectHost;

    @Value("${egov.search.project.task.url}")
    private String projectTaskSearchUrl;

    @Value("${egov.search.project.beneficiary.url}")
    private String projectBeneficiarySearchUrl;
}
