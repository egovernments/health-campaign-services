package org.egov.household.config;

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
public class HouseholdMemberConfiguration {

    @Value("${household.member.kafka.create.topic}")
    private String createTopic;

    @Value("${household.member.kafka.update.topic}")
    private String updateTopic;

    @Value("${household.member.kafka.delete.topic}")
    private String deleteTopic;

    @Value("${household.member.consumer.bulk.create.topic}")
    private String bulkCreateTopic;

    @Value("${household.member.consumer.bulk.update.topic}")
    private String bulkUpdateTopic;

    @Value("${household.member.consumer.bulk.delete.topic}")
    private String bulkDeleteTopic;

    @Value("${egov.individual.host}")
    private String individualServiceHost;

    @Value("${egov.individual.search.url}")
    private String individualServiceSearchUrl;
}
