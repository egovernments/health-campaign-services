package org.egov.household.config;

import org.egov.tracer.kafka.deserializer.HashMapDeserializer;
import org.egov.tracer.kafka.deserializer.ISTTimeZoneHashMapDeserializer;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(GraalVMConfiguration.HouseholdRuntimeHints.class)
public class GraalVMConfiguration {

    public static class HouseholdRuntimeHints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // Register egov-tracer classes for reflection
            hints.reflection()
                .registerType(TypeReference.of(HashMapDeserializer.class),
                    MemberCategory.values())
                .registerType(TypeReference.of(ISTTimeZoneHashMapDeserializer.class),
                    MemberCategory.values())
                .registerType(TypeReference.of("org.egov.tracer.kafka.KafkaTemplateLoggingInterceptors"),
                    MemberCategory.values())
                .registerType(TypeReference.of("org.egov.tracer.model.CustomException"),
                    MemberCategory.values())
                .registerType(TypeReference.of("org.egov.tracer.model.ErrorDetail"),
                    MemberCategory.values())
                .registerType(TypeReference.of("org.egov.tracer.model.ServiceCallException"),
                    MemberCategory.values());

            // Register household models
            hints.reflection()
                .registerType(TypeReference.of("org.egov.household.web.models.Household"),
                    MemberCategory.values())
                .registerType(TypeReference.of("org.egov.household.web.models.HouseholdRequest"),
                    MemberCategory.values())
                .registerType(TypeReference.of("org.egov.household.web.models.HouseholdResponse"),
                    MemberCategory.values())
                .registerType(TypeReference.of("org.egov.household.web.models.HouseholdMember"),
                    MemberCategory.values())
                .registerType(TypeReference.of("org.egov.household.web.models.Address"),
                    MemberCategory.values());

            // Register common classes
            hints.reflection()
                .registerType(TypeReference.of("org.egov.common.contract.request.RequestInfo"),
                    MemberCategory.values())
                .registerType(TypeReference.of("org.egov.common.contract.request.User"),
                    MemberCategory.values())
                .registerType(TypeReference.of("org.egov.common.contract.request.Role"),
                    MemberCategory.values())
                .registerType(TypeReference.of("org.egov.common.contract.response.ResponseInfo"),
                    MemberCategory.values());

            // Register Kafka deserializers
            hints.reflection()
                .registerType(TypeReference.of("org.apache.kafka.common.serialization.StringDeserializer"),
                    MemberCategory.values())
                .registerType(TypeReference.of("org.apache.kafka.common.serialization.StringSerializer"),
                    MemberCategory.values())
                .registerType(TypeReference.of("org.springframework.kafka.support.serializer.JsonDeserializer"),
                    MemberCategory.values())
                .registerType(TypeReference.of("org.springframework.kafka.support.serializer.JsonSerializer"),
                    MemberCategory.values());

            // Register resources
            hints.resources()
                .registerPattern("application*.yml")
                .registerPattern("application*.properties")
                .registerPattern("bootstrap*.yml")
                .registerPattern("bootstrap*.properties")
                .registerPattern("logback*.xml")
                .registerPattern("db/migration/*.sql")
                .registerPattern("tracer.properties")
                .registerPattern("META-INF/spring.factories")
                .registerPattern("META-INF/spring/*.imports");

            // Register serialization hints
            hints.serialization()
                .registerType(TypeReference.of("org.egov.household.web.models.Household"))
                .registerType(TypeReference.of("org.egov.household.web.models.HouseholdRequest"))
                .registerType(TypeReference.of("org.egov.household.web.models.HouseholdResponse"))
                .registerType(TypeReference.of("org.egov.common.contract.request.RequestInfo"))
                .registerType(TypeReference.of("java.util.HashMap"))
                .registerType(TypeReference.of("java.util.LinkedHashMap"))
                .registerType(TypeReference.of("java.util.ArrayList"));

            // Register proxies for Spring AOP
            hints.proxies()
                .registerJdkProxy(
                    TypeReference.of("org.springframework.aop.SpringProxy"),
                    TypeReference.of("org.springframework.aop.framework.Advised"),
                    TypeReference.of("org.springframework.core.DecoratingProxy"));
        }
    }
}