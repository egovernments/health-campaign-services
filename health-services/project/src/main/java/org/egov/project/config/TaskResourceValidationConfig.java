package org.egov.project.config;

import jakarta.validation.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.TaskResource;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.hibernate.validator.cfg.defs.SizeDef;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Relaxes bean validation on {@link TaskResource#getProductVariantId()} and
 * {@link TaskResource#getQuantity()} so that tasks synced from the field app
 * without these values (e.g. REDOSE tasks) are not rejected.
 *
 * The {@code @NotNull} annotations live in the shared health-services-models
 * library; overriding them here keeps the change local to the project service.
 * The size limit on productVariantId is still enforced when a value is present.
 */
@org.springframework.context.annotation.Configuration
@Slf4j
public class TaskResourceValidationConfig {

    @Bean
    @Primary
    public LocalValidatorFactoryBean defaultValidator() {
        return new LocalValidatorFactoryBean() {
            @Override
            protected void postProcessConfiguration(Configuration<?> configuration) {
                if (configuration instanceof HibernateValidatorConfiguration hvConfiguration) {
                    ConstraintMapping mapping = hvConfiguration.createConstraintMapping();
                    mapping.type(TaskResource.class)
                            .field("productVariantId")
                                .ignoreAnnotations(true)
                                .constraint(new SizeDef().min(2).max(64))
                            .field("quantity")
                                .ignoreAnnotations(true);
                    hvConfiguration.addMapping(mapping);
                    log.info("Relaxed @NotNull validation on TaskResource.productVariantId and TaskResource.quantity");
                } else {
                    log.warn("Bean validation provider is not Hibernate Validator; TaskResource constraints not relaxed");
                }
            }
        };
    }
}
