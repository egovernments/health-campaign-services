package org.egov.individual.validators;


import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ResponseFieldsValidator.class)
public @interface ValidResponseFields {

    String message() default "Some responseFields are invalid.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
