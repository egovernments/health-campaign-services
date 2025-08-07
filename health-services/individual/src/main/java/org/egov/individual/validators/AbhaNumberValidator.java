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

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.isValidPattern;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.individual.Constants.ABHA_IDENTIFIER;

@Component
@Slf4j
public class AbhaNumberValidator implements Validator<IndividualBulkRequest, Individual> {

    private final IndividualProperties properties;

    @Autowired
    public AbhaNumberValidator(IndividualProperties properties) {
        this.properties = properties;
    }

    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        log.info("Validating ABHA number for update...");
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        List<Individual> individuals = request.getIndividuals();

        if (!CollectionUtils.isEmpty(individuals)) {
            for (Individual individual : individuals) {
                List<Identifier> identifiers = individual.getIdentifiers();
                if (!CollectionUtils.isEmpty(identifiers)) {
                    List<Identifier> abhaIdentifiers = identifiers.stream()
                            .filter(id -> ABHA_IDENTIFIER.equalsIgnoreCase(id.getIdentifierType()))
                            .collect(Collectors.toList());

                    // 1. Check for more than one ABHA
                    if (abhaIdentifiers.size() > 1) {
                        createDuplicateAbhaError(errorDetailsMap, individual);
                        continue;
                    }

                    // 2. Pattern check if ABHA present
                    if (!abhaIdentifiers.isEmpty()) {
                        Identifier abhaId = abhaIdentifiers.get(0);
                        String abhaNumber = abhaId.getIdentifierId();

                        if (StringUtils.isNotBlank(abhaNumber)) {
                            if (abhaNumber.contains("*")) {
                                // Validate masked format (e.g., ****-****-****-6411)
                                String last4 = abhaNumber.substring(abhaNumber.length() - 4);
                                if (!isValidPattern(last4, "\\d{4}") || abhaNumber.length() != 16) {
                                    createInvalidAbhaError(errorDetailsMap, individual);
                                }
                            } else {
                                // Validate complete ABHA format from properties
                                if (!isValidPattern(abhaNumber, properties.getAbhaPattern())) {
                                    createInvalidAbhaError(errorDetailsMap, individual);
                                }
                            }
                        }
                    }
                }
            }
        }

        return errorDetailsMap;
    }

    private static void createDuplicateAbhaError(Map<Individual, List<Error>> errorDetailsMap, Individual individual) {
        Error error = Error.builder()
                .errorCode("DUPLICATE_ABHA")
                .errorMessage("Only one ABHA number is allowed per individual")
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException("DUPLICATE_ABHA", "Only one ABHA number is allowed per individual"))
                .build();
        populateErrorDetails(individual, error, errorDetailsMap);
    }

    private static void createInvalidAbhaError(Map<Individual, List<Error>> errorDetailsMap, Individual individual) {
        Error error = Error.builder()
                .errorCode("INVALID_ABHA")
                .errorMessage("Invalid ABHA number format")
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException("INVALID_ABHA", "Invalid ABHA number format"))
                .build();
        populateErrorDetails(individual, error, errorDetailsMap);
    }
}
