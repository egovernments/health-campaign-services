package org.egov.fhirtransformer.validator;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.ValueSet;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom FHIR R5 validator configured with DIGIT-specific profiles
 * and terminology support.
 */
@Component
public class CustomFHIRValidator {

    private final FhirContext ctx;
    private final FhirValidator validator;
    private final PrePopulatedValidationSupport support;
    private static final Logger logger = LoggerFactory.getLogger(CustomFHIRValidator.class);

    @Autowired
    public CustomFHIRValidator(FhirContext ctx) {
        this.ctx = ctx;
        this.support = new PrePopulatedValidationSupport(ctx);

        loadProfiles("profiles");

        ValidationSupportChain chain = new ValidationSupportChain(
                new DefaultProfileValidationSupport(ctx),
                new InMemoryTerminologyServerValidationSupport(ctx),
                new CommonCodeSystemsTerminologyService(ctx),
                support
        );
        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(chain);
        this.validator = ctx.newValidator();
        this.validator.registerValidatorModule(instanceValidator);
    }

    /**
     * Loads FHIR StructureDefinitions, CodeSystems, and ValueSets
     * from the specified classpath directory.
     *
     * @param folderName classpath folder containing FHIR profile definitions
     * @throws RuntimeException if profiles cannot be loaded or parsed
     */
    private void loadProfiles(String folderName) {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            if (classLoader == null) {
                throw new RuntimeException("Failed to load FHIR profiles - ClassLoader is null");
            }
            URL resourceCheck = classLoader.getResource(folderName);
            if (resourceCheck == null) {
                throw new RuntimeException("Failed to load FHIR profiles, Profile folder not found: " + folderName);
            }
            Path folderPath = Paths.get(Objects.requireNonNull(
                    getClass().getClassLoader().getResource(folderName)).toURI());

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folderPath, "*.json")) {
                for (Path path : stream) {
                    try (InputStream is = Files.newInputStream(path)) {
                        IBaseResource resource = ctx.newJsonParser().parseResource(is);
                        if (resource instanceof StructureDefinition) {
                            StructureDefinition sd = (StructureDefinition) resource;
                            support.addStructureDefinition(sd);
                            logger.info("Loaded profile: " + sd.getUrl());
                        }
                        else if (resource instanceof CodeSystem cs) {
                            support.addCodeSystem(cs);
                            logger.info("Loaded CodeSystem: " + cs.getUrl());
                        } else if (resource instanceof ValueSet vs) {
                            support.addValueSet(vs);
                            logger.info("Loaded ValueSet: " + vs.getUrl());
                        }
                    }
                }
            }

        } catch (URISyntaxException | NullPointerException | java.io.IOException e) {
            throw new RuntimeException("Failed to load FHIR profiles from /profiles directory", e);
        }
    }

    /**
     * Validates a FHIR JSON payload against configured validation rules.
     *
     * @param fhirJson FHIR resource payload as JSON
     * @return {@link ValidationResult} containing validation errors and warnings
     */
    public ValidationResult validate(String fhirJson) {
        IBaseResource resource = ctx.newJsonParser().parseResource(fhirJson);
        return validator.validateWithResult(resource);
    }
}
