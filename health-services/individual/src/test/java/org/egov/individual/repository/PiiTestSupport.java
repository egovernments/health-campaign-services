package org.egov.individual.repository;

import org.egov.common.pii.PiiEncryptionProperties;
import org.egov.common.pii.PiiHasher;
import org.egov.individual.config.IndividualProperties;

import java.util.List;

/** Builds the PII helpers the repository needs, for tests that construct it by hand. */
final class PiiTestSupport {

    private PiiTestSupport() {
    }

    static IndividualProperties properties() {
        IndividualProperties properties = new IndividualProperties();
        properties.setPiiAdditionalFieldKeys(List.of("weight", "height", "isPregnant"));
        return properties;
    }

    static PiiHasher hasher() {
        PiiEncryptionProperties hashProperties = new PiiEncryptionProperties();
        hashProperties.setHashSecret("test-only-secret");
        hashProperties.setHashLowercaseFields(List.of("givenName"));  // as application.properties sets it
        return new PiiHasher(hashProperties);
    }
}
