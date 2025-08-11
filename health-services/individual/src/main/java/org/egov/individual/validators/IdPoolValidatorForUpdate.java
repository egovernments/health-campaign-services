package org.egov.individual.validators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.models.idgen.IdRecord;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.service.BeneficiaryIdGenService;
import org.egov.common.validator.Validator;
import org.egov.individual.config.IndividualProperties;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.CommonUtils.isValidPattern;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.individual.Constants.*;
import static org.egov.individual.validators.IdPoolValidatorForCreate.validateDuplicateIDs;

/**
 * Validator class for validating beneficiary IDs during update operations on Individual records.
 * Ensures that:
 * 1. The provided beneficiary ID exists.
 * 2. The ID is associated with the user performing the update.
 */
@Component
@Slf4j
@AllArgsConstructor
@Order(value = 12) // Determines execution order among multiple validators
public class IdPoolValidatorForUpdate implements Validator<IndividualBulkRequest, Individual> {

    private final BeneficiaryIdGenService beneficiaryIdGenService;
    private final IndividualProperties individualProperties;

    /**
     * Validates each individual in the bulk request for a valid and authorized beneficiary ID.
     *
     * @param request Bulk request containing individuals and request info
     * @return Map of individuals with their corresponding validation errors (if any)
     */
    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();

        // Get the ID of the currently logged-in user
        String userId = request.getRequestInfo().getUserInfo().getUuid();

        // Skip validation if the feature is disabled in config
        if (!individualProperties.getBeneficiaryIdValidationEnabled()) return errorDetailsMap;

        log.info("Validating beneficiary ID for update");

        List<Individual> individuals = request.getIndividuals();

        validateDuplicateIDs(errorDetailsMap, individuals);

        // Retrieve existing ID records for the individuals
        Map<String, IdRecord> idRecordMap = IdPoolValidatorForCreate
                .getIdRecords(beneficiaryIdGenService, individuals, null, request.getRequestInfo());

        // Existing Unique Beneficiary ID
        Set<String> usedUniqueBeneficiaryIdSet = new HashSet<>();

        // Iterate through individuals and validate beneficiary IDs
        for (Individual individual : individuals) {
            if (!CollectionUtils.isEmpty(individual.getIdentifiers())) {

                // Find the unique beneficiary ID (if present)
                Identifier identifier = individual.getIdentifiers().stream()
                        .filter(id -> UNIQUE_BENEFICIARY_ID.equalsIgnoreCase(id.getIdentifierType()))
                        .findFirst().orElse(null);

                if (identifier != null && StringUtils.isNotBlank(identifier.getIdentifierId())) {
                    String beneficiaryId = identifier.getIdentifierId();
                    if(IdPoolValidatorForCreate.isMaskedId(beneficiaryId)) {
                        if (!isValidMaskedId(beneficiaryId, individualProperties.getBeneficiaryIdLength())) {
                            updateError(errorDetailsMap, individual, INVALID_BENEFICIARY_ID, "The masked beneficiary id '" + beneficiaryId + "' is invalid.");
                        }
                        continue;
                    }
                    // Check if ID exists in the fetched ID records
                    if (!idRecordMap.containsKey(beneficiaryId)) {
                        updateError(errorDetailsMap, individual, INVALID_BENEFICIARY_ID, "The beneficiary id '" + beneficiaryId + "' does not exist");
                    }
                    // Ensure that the ID is associated with the requesting user
                    else if (!userId.equals(idRecordMap.get(beneficiaryId).getLastModifiedBy())) {
                        updateError(errorDetailsMap, individual, INVALID_USER_ID, "This beneficiary id '" + beneficiaryId + "' is dispatched to another user");
                    }
                    // Validate that ID was not used by other individuals in the bulk request
                    else if (usedUniqueBeneficiaryIdSet.contains(beneficiaryId)) {
                        updateError(errorDetailsMap, individual,
                                INVALID_BENEFICIARY_ID,
                                "This beneficiary id '" + beneficiaryId + "' is duplicated for multiple individuals");
                    }
                    usedUniqueBeneficiaryIdSet.add(beneficiaryId);
                }
            }
        }

        return errorDetailsMap;
    }

    /**
     * Helper method to add an error to the error map for a specific individual.
     *
     * @param errorDetailsMap Map tracking individuals and their validation errors
     * @param individual      The individual for whom the error occurred
     * @param errorCode       The error code
     * @param errorMessage    The human-readable error message
     */
    private static void updateError(Map<Individual, List<Error>> errorDetailsMap, Individual individual,
                                    String errorCode, String errorMessage) {

        // Default error message if not provided
        if (StringUtils.isEmpty(errorCode) || StringUtils.isEmpty(errorMessage)) {
            errorCode = INVALID_BENEFICIARY_ID;
            errorMessage = "Invalid beneficiary id";
        }

        // Build error object
        Error error = Error.builder()
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException(errorCode, errorMessage))
                .build();

        // Add error to the individual's error map
        populateErrorDetails(individual, error, errorDetailsMap);
    }

    public static boolean isValidMaskedId(String beneficiaryId, Integer length) {
        // get the last 4 digits
        String last4Digits = beneficiaryId
                .substring(beneficiaryId.length() - 4);
        // regex to check if last 4 digits are numbers
        String regex = "[0-9]+";
        return isValidPattern(last4Digits, regex) && beneficiaryId.length() == length;
    }
}
