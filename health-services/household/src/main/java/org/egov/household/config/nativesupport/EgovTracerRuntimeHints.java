package org.egov.household.config.nativesupport;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * GraalVM native image runtime hints for egov-tracer library.
 * Registers reflection metadata for Kafka deserializers and servlet proxies.
 */
public class EgovTracerRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Register Kafka deserializer classes
        registerKafkaDeserializers(hints, classLoader);

        // Register servlet proxies
        registerServletProxies(hints);

        // Register common data structures
        registerDataStructures(hints);

        // Register resources
        registerResources(hints);
    }

    private void registerKafkaDeserializers(RuntimeHints hints, ClassLoader classLoader) {
        try {
            // HashMapDeserializer - used by Kafka consumers
            Class<?> hashMapDeserializer = Class.forName(
                "org.egov.tracer.kafka.deserializer.HashMapDeserializer",
                false,
                classLoader);

            hints.reflection().registerType(hashMapDeserializer,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS);

            // ErrorHashMapDeserializer
            Class<?> errorDeserializer = Class.forName(
                "org.egov.tracer.kafka.deserializer.ErrorHashMapDeserializer",
                false,
                classLoader);

            hints.reflection().registerType(errorDeserializer,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS);

        } catch (ClassNotFoundException e) {
            // Tracer classes not available, skip
        }

        // Standard Kafka serializers/deserializers
        hints.reflection()
            .registerType(org.apache.kafka.common.serialization.StringDeserializer.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS)
            .registerType(org.apache.kafka.common.serialization.StringSerializer.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS);
    }

    private void registerServletProxies(RuntimeHints hints) {
        // Register proxies created by tracer for request/response interception
        hints.proxies()
            .registerJdkProxy(HttpServletRequest.class)
            .registerJdkProxy(HttpServletResponse.class);
    }

    private void registerDataStructures(RuntimeHints hints) {
        // HashMap and LinkedHashMap used in deserialization
        hints.reflection()
            .registerType(java.util.HashMap.class,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.INVOKE_PUBLIC_METHODS)
            .registerType(java.util.LinkedHashMap.class,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.INVOKE_PUBLIC_METHODS);
    }

    private void registerResources(RuntimeHints hints) {
        // Application configuration files
        hints.resources()
            .registerPattern("application.properties")
            .registerPattern("application*.properties")
            .registerPattern("application.yml")
            .registerPattern("application*.yml")
            .registerPattern("db/migration/*");
    }
}