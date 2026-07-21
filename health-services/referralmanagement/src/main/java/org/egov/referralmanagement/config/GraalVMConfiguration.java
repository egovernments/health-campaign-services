package org.egov.referralmanagement.config;

import org.egov.tracer.kafka.deserializer.HashMapDeserializer;
import org.egov.tracer.kafka.deserializer.ISTTimeZoneHashMapDeserializer;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(GraalVMConfiguration.ReferralManagementRuntimeHints.class)
public class GraalVMConfiguration {

    static class ReferralManagementRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection()
                    .registerType(TypeReference.of(HashMapDeserializer.class), MemberCategory.values())
                    .registerType(TypeReference.of(ISTTimeZoneHashMapDeserializer.class), MemberCategory.values());
        }
    }
}
