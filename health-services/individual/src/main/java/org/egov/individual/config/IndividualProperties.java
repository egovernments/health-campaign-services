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

    @Value("${egov.beneficiary.idgen.integration.enabled:false}")
    private Boolean beneficiaryIdGenIntegrationEnabled;

    @Value("${individual.beneficiary.id.length}")
    private Integer beneficiaryIdLength;

    // ---- PII encryption at rest ----

    /** enc-client SecurityPolicy model used to encrypt the PII values (attribute "value"). */
    @Value("${individual.pii.encrypt.model:IndividualPiiEncrypt}")
    private String piiEncryptModel;

    /** enc-client SecurityPolicy model used to decrypt the PII values (one node per value). */
    @Value("${individual.pii.decrypt.model:IndividualPiiDecrypt}")
    private String piiDecryptModel;

    /** additionalFields keys whose values hold health data and are stored encrypted. */
    @Value("#{'${individual.pii.additional.fields.keys:weight,height,isPregnant}'.split(',')}")
    private List<String> piiAdditionalFieldKeys;

    // The bulk envelope, DLQ, enc-service timeout, circuit-breaker and hash-secret settings are read by
    // org.egov.common.pii (health-services-common); application.properties maps the individual.* names into them.

    /** Circuit open period, same value as the library's egov.enc.circuit.open.ms; the 503 answer sends it as Retry-After. */
    @Value("${individual.enc.circuit.open.ms:30000}")
    private Long encCircuitOpenMs;



}
