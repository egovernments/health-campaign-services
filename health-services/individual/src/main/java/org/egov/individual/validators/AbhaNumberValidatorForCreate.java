package org.egov.individual.validators;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.individual.config.IndividualProperties;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.isValidPattern;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.individual.Constants.AADHAR_IDENTIFIER;
import static org.egov.individual.Constants.ABHA_IDENTIFIER;

@Component
@Slf4j
public class AbhaNumberValidatorForCreate implements Validator<IndividualBulkRequest, Individual> {

    private final IndividualProperties properties;

    @Autowired
    public AbhaNumberValidatorForCreate(IndividualProperties properties) {
        this.properties = properties;
    }

    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        log.info("Validating ABHA number for create");
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        List<Individual> individuals = request.getIndividuals();

        if (!CollectionUtils.isEmpty(individuals)) {
            for (Individual individual : individuals) {
                List<Identifier> identifiers = individual.getIdentifiers();

                if (!CollectionUtils.isEmpty(identifiers)) {
                    Identifier abhaIdentifier = identifiers.stream()
                            .filter(id -> ABHA_IDENTIFIER.equalsIgnoreCase(id.getIdentifierType()))
                            .findFirst()
                            .orElse(null);

                    Identifier aadhaarIdentifier = identifiers.stream()
                            .filter(id -> AADHAR_IDENTIFIER.equalsIgnoreCase(id.getIdentifierType()))
                            .findFirst()
                            .orElse(null);

                    // ABHA identifier present but no valid Aadhaar → invalid
                    if (abhaIdentifier != null && aadhaarIdentifier == null) {
                        createMissingAadhaarError(errorDetailsMap, individual);
                    }

                    // If ABHA is present and Aadhaar is also present, validate ABHA pattern
                    if (abhaIdentifier != null && aadhaarIdentifier != null &&
                            StringUtils.isNotBlank(abhaIdentifier.getIdentifierId()) &&
                            !isValidPattern(abhaIdentifier.getIdentifierId(), properties.getAbhaPattern())) {
                        createInvalidAbhaError(errorDetailsMap, individual);
                    }
                }
            }
        }
        return errorDetailsMap;
    }

    private static void createMissingAadhaarError(Map<Individual, List<Error>> errorDetailsMap, Individual individual) {
        Error error = Error.builder()
                .errorMessage("ABHA cannot be created without a valid Aadhaar identifier")
                .errorCode("AADHAAR_REQUIRED_FOR_ABHA")
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException("AADHAAR_REQUIRED_FOR_ABHA", "ABHA cannot be created without a valid Aadhaar identifier"))
                .build();
        populateErrorDetails(individual, error, errorDetailsMap);
    }

    private static void createInvalidAbhaError(Map<Individual, List<Error>> errorDetailsMap, Individual individual) {
        Error error = Error.builder()
                .errorMessage("Invalid ABHA number")
                .errorCode("INVALID_ABHA")
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException("INVALID_ABHA", "Invalid ABHA number"))
                .build();
        populateErrorDetails(individual, error, errorDetailsMap);
    }
}
