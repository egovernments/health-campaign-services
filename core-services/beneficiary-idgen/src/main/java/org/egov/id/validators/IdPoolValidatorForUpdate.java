package org.egov.id.validators;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.models.core.EgovModel;
import org.egov.common.models.idgen.IdRecord;
import org.egov.common.models.idgen.IdRecordBulkRequest;
import org.egov.common.models.idgen.IdStatus;
import org.egov.common.validator.Validator;
import org.egov.id.config.PropertiesManager;
import org.egov.id.repository.IdRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

@Component
@Slf4j
@AllArgsConstructor
@Order(value = 12)
public class IdPoolValidatorForUpdate implements Validator<IdRecordBulkRequest, IdRecord> {

    private final PropertiesManager propertiesManager;
    private final IdRepository idRepo;

    /**
     * Validates a bulk request for updating IdRecords.
     * Checks that:
     * - ID validation is enabled via properties.
     * - Each IdRecord has a valid status (matching enum).
     * - The ID string is present and exists in the repository.
     * Returns a map of IdRecords to their respective validation errors.
     *
     * @param request Bulk request containing IdRecords to validate
     * @return Map of IdRecords to List of validation errors
     */
    @Override
    public Map<IdRecord, List<Error>> validate(IdRecordBulkRequest request) {
        Map<IdRecord, List<Error>> errorDetailsMap = new HashMap<>();

        // Skip validation if disabled via configuration
        if (!propertiesManager.getIdValidationEnabled()) return errorDetailsMap;

        log.info("Validating IDs for update");

        List<IdRecord> idRecords = request.getIdRecords();

        if (CollectionUtils.isEmpty(idRecords)) return errorDetailsMap;

        // Assuming all records have the same tenantId, fetch tenantId from first record
        String tenantId = idRecords.get(0).getTenantId();

        // Fetch existing IdRecords from DB based on IDs and tenantId
        Map<String, IdRecord> idRecordMap = idRepo.findByIDsAndStatus(
                idRecords.stream()
                        .map(IdRecord::getId)
                        .collect(Collectors.toList()),
                null,  // Status filter is null, i.e., fetch irrespective of status
                tenantId
        ).stream().collect(Collectors.toMap(EgovModel::getId, record -> record));

        // Validate each IdRecord in the request
        if (!idRecords.isEmpty()) {
            for (IdRecord idRecord : idRecords) {
                try {
                    // Validate if the status string corresponds to a valid enum constant
                    IdStatus.valueOf(idRecord.getStatus().toUpperCase());
                } catch (Exception e) {
                    // Invalid status found; add an error entry
                    log.error("Invalid status found for ID: {}", idRecord.getId(), e);
                    updateError(errorDetailsMap, idRecord, e.getMessage());
                }

                // Validate that the ID is not blank and exists in the repository
                if (StringUtils.isBlank(idRecord.getId()) || !idRecordMap.containsKey(idRecord.getId())) {
                    log.error("ID is blank or does not exist in the repository: {}", idRecord.getId());
                    updateError(errorDetailsMap, idRecord, "ID is blank or does not exist in the repository: " + idRecord.getId());
                }
            }
        }
        return errorDetailsMap;
    }

    /**
     * Helper method to update the error map with an INVALID_ID error for the given IdRecord.
     *
     * @param errorDetailsMap Map tracking errors for IdRecords
     * @param idRecord The IdRecord that failed validation
     */
    private static void updateError(Map<IdRecord, List<Error>> errorDetailsMap, IdRecord idRecord, String errorMessage) {
        String errorCode = "INVALID_ID";

        // Create a non-recoverable error with the specified code and message
        Error error = Error.builder()
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException(errorCode, errorMessage))
                .build();

        // Populate the error details map with this error for the given IdRecord
        populateErrorDetails(idRecord, error, errorDetailsMap);
    }
}
