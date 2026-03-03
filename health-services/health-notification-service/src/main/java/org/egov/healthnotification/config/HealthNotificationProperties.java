package org.egov.healthnotification.config;

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
public class HealthNotificationProperties {

    // Kafka Topics
    @Value("${project.task.consumer.create.topic}")
    private String projectTaskCreateTopic;

    @Value("${kafka.topics.notification.sms}")
    private String smsNotificationTopic;

    // Tenant Configuration
    @Value("${state.level.tenant.id}")
    private String stateLevelTenantId;

    // Household Service
    @Value("${egov.household.host}")
    private String householdServiceHost;

    @Value("${egov.household.search.url}")
    private String householdSearchUrl;

    @Value("${egov.household.member.search.url}")
    private String householdMemberSearchUrl;

    // Individual Service
    @Value("${egov.individual.host}")
    private String individualServiceHost;

    @Value("${egov.individual.search.url}")
    private String individualSearchUrl;

    // Project Service (includes project and beneficiary endpoints)
    @Value("${egov.project.host}")
    private String projectHost;

    @Value("${egov.project.search.url}")
    private String projectSearchUrl;

    @Value("${egov.project.beneficiary.search.url}")
    private String projectBeneficiarySearchUrl;

    // MDMS Configuration
    @Value("${egov.mdms.host}")
    private String mdmsHost;

    @Value("${egov.mdms.search.endpoint}")
    private String mdmsSearchEndpoint;

    @Value("${egov.mdms.search.v2.endpoint}")
    private String mdmsSearchV2Endpoint;

    @Value("${mdms.notification.module}")
    private String notificationModule;

    @Value("${mdms.project.module}")
    private String projectModule;

    // Localization
    @Value("${egov.localization.host}")
    private String localizationHost;

    @Value("${egov.localization.context.path}")
    private String localizationContextPath;

    @Value("${egov.localization.search.endpoint}")
    private String localizationSearchEndpoint;

    @Value("${egov.localization.statelevel}")
    private Boolean localizationStateLevel;

    @Value("${egov.localization.notification.module}")
    private String localizationNotificationModule;

    // SMS Configuration
    @Value("${notification.sms.enabled}")
    private Boolean smsNotificationEnabled;
}
