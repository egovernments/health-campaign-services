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
    @Value("${project.adverseevent.kafka.create.topic}")
    private String createAdverseEventTopic;

    @Value("${project.adverseevent.kafka.update.topic}")
    private String updateAdverseEventTopic;

    @Value("${project.adverseevent.kafka.delete.topic}")
    private String deleteAdverseEventTopic;

    @Value("${project.adverseevent.consumer.bulk.create.topic}")
    private String createAdverseEventBulkTopic;

    @Value("${project.adverseevent.consumer.bulk.update.topic}")
    private String updateAdverseEventBulkTopic;

    @Value("${project.adverseevent.consumer.bulk.delete.topic}")
    private String deleteAdverseEventBulkTopic;

    @Value("${egov.project.task.host}")
    private String projectTaskHost;

    @Value("${egov.search.project.task.url}")
    private String projectTaskSearchUrl;
}
