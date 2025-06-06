package org.egov.individual.validators;

import org.egov.individual.config.IndividualProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResponseFieldsValidator implements ConstraintValidator<ValidResponseFields, List<String>> {

    @Autowired
    private IndividualProperties individualProperties;

    @Override
    public boolean isValid(List<String> fields, ConstraintValidatorContext context) {
        List<String> allowedFields = individualProperties.getAllowedResponseFields();
        if (fields == null)
            return true;

        boolean allValid = fields.stream().allMatch(field -> allowedFields.contains(field)
        );

        if (!allValid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "Invalid responseFields. Allowed values are: " + String.join(", ", allowedFields))
                    .addConstraintViolation();
        }

        return allValid;
    }
}
