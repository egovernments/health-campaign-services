package org.egov.individual.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.egov.individual.web.models.IndividualMappedSearch;

import java.util.List;

public class ExactlyOneFieldValidator implements ConstraintValidator<ExactlyOneField, IndividualMappedSearch> {

    @Override
    public boolean isValid(IndividualMappedSearch value, ConstraintValidatorContext context) {
        if (value == null)
            return true;

        List<String> usernames = value.getUsername();
        List<String> mobiles = value.getMobileNumber();

        boolean hasUsername = usernames != null && !usernames.isEmpty();
        boolean hasMobile = mobiles != null && !mobiles.isEmpty();

        return hasUsername ^ hasMobile; // Only one should be present
    }
}
