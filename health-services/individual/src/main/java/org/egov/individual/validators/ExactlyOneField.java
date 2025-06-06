package org.egov.individual.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ ElementType.TYPE }) // this means it can only be used on classes, not fields
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExactlyOneFieldValidator.class)
@Documented
public @interface ExactlyOneField {
    String message() default "Exactly one of 'username' or 'mobileNumber' must be provided";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
