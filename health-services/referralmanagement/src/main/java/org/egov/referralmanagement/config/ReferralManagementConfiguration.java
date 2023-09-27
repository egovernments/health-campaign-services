package org.egov.referralmanagement.config;

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
public class ReferralManagementConfiguration {
    @Value("${referralmanagement.adverseevent.kafka.create.topic}")
    private String createAdverseEventTopic;

    @Value("${referralmanagement.adverseevent.kafka.update.topic}")
    private String updateAdverseEventTopic;

    @Value("${referralmanagement.adverseevent.kafka.delete.topic}")
    private String deleteAdverseEventTopic;

    @Value("${referralmanagement.adverseevent.consumer.bulk.create.topic}")
    private String createAdverseEventBulkTopic;

    @Value("${referralmanagement.adverseevent.consumer.bulk.update.topic}")
    private String updateAdverseEventBulkTopic;

    @Value("${referralmanagement.adverseevent.consumer.bulk.delete.topic}")
    private String deleteAdverseEventBulkTopic;

    @Value("${egov.project.host}")
    private String projectHost;

    @Value("${egov.search.project.task.url}")
    private String projectTaskSearchUrl;

    @Value("${egov.search.project.beneficiary.url}")
    private String projectBeneficiarySearchUrl;
}
