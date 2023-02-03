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

}
