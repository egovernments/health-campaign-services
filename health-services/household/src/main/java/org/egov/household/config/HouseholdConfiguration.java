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
public class HouseholdConfiguration {

    @Value("${household.kafka.create.topic}")
    private String createTopic;

    @Value("${household.consumer.bulk.create.topic}")
    private String consumerCreateTopic;

    @Value("${household.kafka.update.topic}")
    private String updateTopic;

    @Value("${household.consumer.bulk.update.topic}")
    private String consumerUpdateTopic;

    @Value("${household.kafka.delete.topic}")
    private String deleteTopic;

    @Value("${household.consumer.bulk.delete.topic}")
    private String consumerDeleteTopic;

    @Value("${household.idgen.id.format}")
    private String idgenFormat;

    @Value("${egov.boundary.host}")
    private String boundaryServiceHost;

    @Value("${egov.boundary.search.url}")
    private String boundarySearchUrl;

    @Value("${household.type.same.validation}")
    private boolean householdTypeSameValidation;

    @Value("${household.type.community.creator.role}")
    private String communityHouseholdCreatorRoleCode;

    // ---- PII encryption at rest ----

    /** enc-client SecurityPolicy model used to encrypt the address values (attribute "value"). */
    @Value("${household.pii.encrypt.model:HouseholdPiiEncrypt}")
    private String piiEncryptModel;

    /** enc-client SecurityPolicy model used to decrypt the address values (one node per value). */
    @Value("${household.pii.decrypt.model:HouseholdPiiDecrypt}")
    private String piiDecryptModel;

    // The bulk envelope, DLQ, enc-service timeout and circuit-breaker settings are read by
    // org.egov.common.pii (health-services-common); application.properties maps the household.* names into them.

    /** Circuit open period, same value as the library's egov.enc.circuit.open.ms; the 503 answer sends it as Retry-After. */
    @Value("${household.enc.circuit.open.ms:30000}")
    private Long encCircuitOpenMs;
}
