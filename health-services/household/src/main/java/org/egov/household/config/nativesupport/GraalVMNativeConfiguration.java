package org.egov.household.config.nativesupport;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * GraalVM Native Image configuration for the household service.
 * Automatically registers runtime hints for reflection, proxies, and resources.
 */
@Configuration
@ImportRuntimeHints(EgovTracerRuntimeHints.class)
public class GraalVMNativeConfiguration {
    // This configuration class imports the runtime hints
    // Spring AOT will process these hints during native image compilation
}