package org.egov.healthnotification.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

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

    // Scheduled Notification Persister Topics
    @Value("${scheduled.notification.save.topic}")
    private String scheduledNotificationSaveTopic;

    @Value("${scheduled.notification.update.topic}")
    private String scheduledNotificationUpdateTopic;

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

    @Value("${egov.localization.supported.locales}")
    private List<String> supportedLocales;

    // SMS Configuration
    @Value("${notification.sms.enabled}")
    private Boolean smsNotificationEnabled;

    // Notification Timezone — ALL LocalDate conversions MUST use this
    @Value("${notification.timezone:UTC}")
    private String notificationTimezone;

    // Scheduler Configuration
    @Value("${notification.scheduler.batch.size:100}")
    private Integer schedulerBatchSize;

    @Value("${notification.scheduler.max.fetch:10000}")
    private Integer schedulerMaxFetch;

    // Encryption Service
    @Value("${egov.enc.host}")
    private String encryptionServiceHost;

    @Value("${egov.enc.encrypt.endpoint}")
    private String encryptionEndpoint;

    @Value("${egov.enc.decrypt.endpoint}")
    private String decryptionEndpoint;

    // Stock Kafka Consumer Topics
    @Value("${stock.consumer.create.topic}")
    private String stockCreateTopic;

    @Value("${stock.consumer.update.topic}")
    private String stockUpdateTopic;

    // HFReferral Kafka Consumer Topics
    @Value("${hfreferral.consumer.create.topic}")
    private String hfReferralCreateTopic;

    @Value("${hfreferral.consumer.update.topic}")
    private String hfReferralUpdateTopic;

    // Push Notification Kafka Topic
    @Value("${kafka.topics.notification.push}")
    private String pushNotificationTopic;

    @Value("${notification.push.enabled:true}")
    private Boolean pushNotificationEnabled;

    // Push Notification Service (REST — reserved for future use)
    @Value("${egov.push.notification.host}")
    private String pushNotificationHost;

    @Value("${egov.push.notification.send.url}")
    private String pushNotificationSendUrl;

    // Stock Service
    @Value("${egov.stock.host}")
    private String stockServiceHost;

    @Value("${egov.stock.search.url}")
    private String stockSearchUrl;

    // Facility Service
    @Value("${egov.facility.host}")
    private String facilityServiceHost;

    @Value("${egov.facility.search.url}")
    private String facilitySearchUrl;

    // Project Facility Service (resolves projectFacilityId → facilityId)
    @Value("${egov.project.facility.search.url}")
    private String projectFacilitySearchUrl;
}
