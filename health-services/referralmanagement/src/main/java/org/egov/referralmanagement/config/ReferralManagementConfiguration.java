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
    @Value("${referralmanagement.sideeffect.kafka.create.topic}")
    private String createSideEffectTopic;

    @Value("${referralmanagement.sideeffect.kafka.update.topic}")
    private String updateSideEffectTopic;

    @Value("${referralmanagement.sideeffect.kafka.delete.topic}")
    private String deleteSideEffectTopic;

    @Value("${referralmanagement.sideeffect.consumer.bulk.create.topic}")
    private String createSideEffectBulkTopic;

    @Value("${referralmanagement.sideeffect.consumer.bulk.update.topic}")
    private String updateSideEffectBulkTopic;

    @Value("${referralmanagement.sideeffect.consumer.bulk.delete.topic}")
    private String deleteSideEffectBulkTopic;

    @Value("${egov.project.host}")
    private String projectHost;

    @Value("${egov.search.project.task.url}")
    private String projectTaskSearchUrl;

    @Value("${egov.search.project.beneficiary.url}")
    private String projectBeneficiarySearchUrl;
}
