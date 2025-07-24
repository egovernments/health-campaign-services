package org.egov.individual.config;

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
public class IndividualProperties {

    @Value("${individual.producer.save.topic}")
    private String saveIndividualTopic;

    @Value("${individual.producer.update.topic}")
    private String updateIndividualTopic;

    @Value("${individual.producer.delete.topic}")
    private String deleteIndividualTopic;

    @Value("${individual.producer.update.user.id.topic}")
    private String updateUserIdTopic;

    @Value("${individual.consumer.bulk.create.topic}")
    private String bulkSaveIndividualTopic;

    @Value("${individual.consumer.bulk.update.topic}")
    private String bulkUpdateIndividualTopic;

    @Value("${individual.consumer.bulk.delete.topic}")
    private String bulkDeleteIndividualTopic;

    @Value("${idgen.individual.id.format}")
    private String individualId;

    @Value("${aadhaar.pattern}")
    private String aadhaarPattern;

    @Value("${mobile.pattern}")
    private String mobilePattern;

    @Value(("${state.level.tenant.id}"))
    private String stateLevelTenantId;

    @Value(("${user.sync.enabled}"))
    private boolean userSyncEnabled;

    @Value(("${user.service.user.type}"))
    private String userServiceUserType;

    @Value(("${user.service.account.locked}"))
    private boolean userServiceAccountLocked;

    //SMS notification
    @Value("${notification.sms.enabled}")
    private Boolean isSMSEnabled;

    @Value("${kafka.topics.notification.sms}")
    private String smsNotifTopic;

    @Value("${notification.sms.disabled.roles}")
    private List<String> smsDisabledRoles;

    //Localization
    @Value("${egov.localization.host}")
    private String localizationHost;

    @Value("${egov.localization.context.path}")
    private String localizationContextPath;

    @Value("${egov.localization.search.endpoint}")
    private String localizationSearchEndpoint;

    @Value("${egov.localization.statelevel}")
    private Boolean isLocalizationStateLevel;

    @Value("${egov.boundary.host}")
    private String boundaryServiceHost;

    @Value("${egov.boundary.search.url}")
    private String boundarySearchUrl;

    @Value("${individual.beneficiary.id.validation.enabled:false}")
    private Boolean beneficiaryIdValidationEnabled;

    @Value("${household.member.kafka.create.topic}")
    private String createHouseholdMemberTopic;

    @Value("${household.member.kafka.update.topic}")
    private String updateHouseholdMemberTopic;

    @Value("${household.member.kafka.delete.topic}")
    private String deleteHouseholdMemberTopic;

    @Value("${household.member.consumer.bulk.create.topic}")
    private String createHouseholdMemberBulkTopic;

    @Value("${household.member.consumer.bulk.update.topic}")
    private String updateHouseholdMemberBulkTopic;

    @Value("${household.member.consumer.bulk.delete.topic}")
    private String deleteHouseholdMemberBulkTopic;

    @Value("${household.kafka.create.topic}")
    private String createHouseholdTopic;

    @Value("${household.consumer.bulk.create.topic}")
    private String createHouseholdBulkTopic;

    @Value("${household.kafka.update.topic}")
    private String updateHouseholdTopic;

    @Value("${household.consumer.bulk.update.topic}")
    private String updateHouseholdBulkTopic;

    @Value("${household.kafka.delete.topic}")
    private String deleteHouseholdTopic;

    @Value("${household.consumer.bulk.delete.topic}")
    private String deleteHouseholdBulkTopic;

    @Value("${household.idgen.id.format}")
    private String householdIdFormat;

    @Value("${household.type.same.validation}")
    private boolean householdTypeSameValidation;

    @Value("${household.type.community.creator.role}")
    private String communityHouseholdCreatorRoleCode;

}
