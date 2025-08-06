package org.egov.individual.validators;

import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.common.models.core.EgovModel;
import org.egov.common.models.idgen.IdDispatchResponse;
import org.egov.common.models.idgen.IdRecord;
import org.egov.common.models.idgen.IdStatus;
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
import org.springframework.util.ObjectUtils;

import static org.egov.common.utils.CommonUtils.isValidPattern;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueSubEntity;
import static org.egov.individual.Constants.*;

@Component
@Slf4j
@AllArgsConstructor
@Order(value = 7)
public class IdPoolValidatorForCreate implements Validator<IndividualBulkRequest, Individual> {

    private final BeneficiaryIdGenService beneficiaryIdGenService;
    private final IndividualProperties individualProperties;

    /**
     * Validates if the provided beneficiary IDs are valid, dispatched, and belong to the requesting user.
     *
     * @param request Bulk request containing individuals and request info
     * @return Map of Individuals and associated list of validation Errors
     */
    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();

        String userId = request.getRequestInfo().getUserInfo().getUuid();

        // Skip validation if feature is disabled in configuration
        if (!individualProperties.getBeneficiaryIdValidationEnabled()) return errorDetailsMap;

        log.info("Validating beneficiary ID for create");

        List<Individual> individuals = request.getIndividuals();

        validateDuplicateIDs(errorDetailsMap, individuals);

        // Fetch ID records from IDGEN service
        Map<String, IdRecord> idRecordMap = getIdRecords(beneficiaryIdGenService, individuals, null, request.getRequestInfo());

        // Existing Unique Beneficiary ID
        Set<String> usedUniqueBeneficiaryIdSet = new HashSet<>();

        for (Individual individual : individuals) {
            if (!CollectionUtils.isEmpty(individual.getIdentifiers())) {
                // Fetch the unique beneficiary identifier, if present
                Identifier identifier = individual.getIdentifiers().stream()
                        .filter(id -> UNIQUE_BENEFICIARY_ID.equalsIgnoreCase(id.getIdentifierType()))
                        .findFirst().orElse(null);

                if (identifier != null && StringUtils.isNotBlank(identifier.getIdentifierId())) {
                    String idValue = identifier.getIdentifierId();

                    // Validate existence of ID
                    if (!idRecordMap.containsKey(idValue)) {
                        createError(errorDetailsMap, individual, null, INVALID_BENEFICIARY_ID, "The beneficiary id '" + idValue + "' does not exist");
                    }
                    // Validate that ID is in DISPATCHED state
                    else if (!IdStatus.DISPATCHED.name().equals(idRecordMap.get(idValue).getStatus())) {
                        createError(errorDetailsMap, individual,
                                idRecordMap.get(idValue).getStatus(), INVALID_BENEFICIARY_ID,
                                "The beneficiary id '" + idValue + "' status is not in DISPATCHED state");
                    }
                    // Validate that ID was dispatched to this user
                    else if (!userId.equals(idRecordMap.get(idValue).getLastModifiedBy())) {
                        createError(errorDetailsMap, individual,
                                idRecordMap.get(idValue).getStatus(), INVALID_USER_ID,
                                "This beneficiary id '" + idValue + "' is dispatched to another user");
                    }
                    // Validate that ID was not used by other individuals in the bulk request
                    else if (usedUniqueBeneficiaryIdSet.contains(idValue)) {
                        createError(errorDetailsMap, individual,
                                idRecordMap.get(idValue).getStatus(), INVALID_BENEFICIARY_ID,
                                "This beneficiary id '" + idValue + "' is duplicated for multiple individuals");
                    }
                    usedUniqueBeneficiaryIdSet.add(idValue);
                }
            }
        }

        return errorDetailsMap;
    }

    /**
     * Adds an error entry to the error map for a specific individual.
     *
     * @param errorDetailsMap Map of errors
     * @param individual      The individual record being validated
     * @param status          Status of the ID (if available)
     * @param errorCode       Error code for reporting
     * @param errorMessage    Human-readable error message
     */
    private static void createError(Map<Individual, List<Error>> errorDetailsMap, Individual individual, String status, String errorCode, String errorMessage) {
        if (StringUtils.isEmpty(errorCode) || StringUtils.isEmpty(errorMessage)) {
            errorCode = INVALID_BENEFICIARY_ID;
            errorMessage = String.format("Invalid beneficiary id, status: %s", status);
        }
        Error error = Error.builder()
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException(errorCode, errorMessage))
                .build();

        populateErrorDetails(individual, error, errorDetailsMap);
    }

    /**
     * Fetches beneficiary ID records for a list of individuals from the IDGEN system.
     *
     * @param beneficiaryIdGenService Service for fetching ID records
     * @param individuals             List of individual entities to validate
     * @param status                  Optional filter for status
     * @param requestInfo             Request metadata for auditing
     * @return Map of ID strings to corresponding ID record objects
     */
    public static Map<String, IdRecord> getIdRecords(BeneficiaryIdGenService beneficiaryIdGenService, List<Individual> individuals, String status, RequestInfo requestInfo) {
        // Extract unique beneficiary IDs from the identifiers
        List<String> beneficiaryIds = individuals.stream()
                .flatMap(d -> Optional.ofNullable(d.getIdentifiers())
                        .orElse(Collections.emptyList())
                        .stream()
                        .filter(identifier -> UNIQUE_BENEFICIARY_ID.equals(identifier.getIdentifierType()))
                        .findFirst()
                        .stream())
                .map(identifier -> String.valueOf(identifier.getIdentifierId()))
                .filter(id -> !isMaskedId(id))
                .toList();

        Map<String, IdRecord> idMap = new HashMap<>();
        if (ObjectUtils.isEmpty(beneficiaryIds)) return idMap;

        // Assuming all individuals belong to the same tenant
        String tenantId = individuals.get(0).getTenantId();

        // Fetch ID records using the service
        IdDispatchResponse idDispatchResponse = beneficiaryIdGenService.searchIdRecord(
                beneficiaryIds,
                status,
                tenantId,
                requestInfo
        );

        // Convert response list to a map keyed by ID
        return idDispatchResponse.getIdResponses().stream()
                .collect(Collectors.toMap(EgovModel::getId, d -> d));
    }

    public static void validateDuplicateIDs(Map<Individual, List<Error>> errorDetailsMap, List<Individual> individuals) {
        Set<String> uniqueIds = new HashSet<>();
        for (Individual individual: individuals) {
            if (individual.getIdentifiers() == null) continue;
            List<String> identifiers = individual.getIdentifiers().stream()
                    .filter(id -> UNIQUE_BENEFICIARY_ID.equalsIgnoreCase(id.getIdentifierType()))
                    .map(Identifier::getIdentifierId)
                    .filter(identifierId -> !isMaskedId(identifierId))
                    .toList();
            if (!identifiers.isEmpty() && !identifiers.stream().allMatch(uniqueIds::add)) {
                log.error("Duplicate beneficiary ID found in the bulk request for individual {}", individual.getClientReferenceId());
                Error error = getErrorForUniqueSubEntity();
                populateErrorDetails(individual, error, errorDetailsMap);
            }
        }
    }

    public static boolean isValidMaskedId(String beneficiaryId) {
        // get the last 4 digits
        String last4Digits = beneficiaryId
                .substring(beneficiaryId.length() - 4);
        // regex to check if last 4 digits are numbers
        String regex = "[0-9]+";
        return isValidPattern(last4Digits, regex) && beneficiaryId.length() == 12;
    }

    public static boolean isMaskedId(String beneficiaryId) {
        return beneficiaryId.contains("*");
    }
}
