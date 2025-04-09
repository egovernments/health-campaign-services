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

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

@Component
@Slf4j
@AllArgsConstructor
@Order(value = 12)
public class IdPoolValidatorForUpdate implements Validator<IdRecordBulkRequest, IdRecord> {

    private final PropertiesManager propertiesManager;

    private final IdRepository idRepo;


    @Override
    public Map<IdRecord, List<Error>> validate(IdRecordBulkRequest request) {
        Map<IdRecord, List<Error>> errorDetailsMap = new HashMap<>();
        if (!propertiesManager.getIdValidationEnabled()) return errorDetailsMap;

        log.info("validating id for update");
        List<IdRecord> idRecords = request.getIdRecords();

        String tenantId = idRecords.get(0).getTenantId();
        Map<String, IdRecord> idRecordMap = idRepo.findByIDsAndStatus(
                idRecords.stream().map(idRecord ->  idRecord.getId()).collect(Collectors.toList()),
                null,
                tenantId
        ).stream().collect(Collectors.toMap(EgovModel::getId , d -> d));

        if (!idRecords.isEmpty()) {
            for (IdRecord idRecord : idRecords) {
                try {
                    IdStatus status = IdStatus.valueOf(idRecord.getStatus().toUpperCase());
                    // valid status, you can now use the enum if needed
                } catch (IllegalArgumentException e) {
                    // Invalid status value
                    updateError(errorDetailsMap, idRecord);
                }
                if (!StringUtils.isNotBlank(idRecord.getId())) {
                    if (!idRecordMap.containsKey(idRecord.getId())) {
                        updateError(errorDetailsMap, idRecord);
                    }

                }
            }
        }
        return errorDetailsMap;
    }

    private static void updateError(Map<IdRecord, List<Error>> errorDetailsMap, IdRecord idRecord) {
        String errorCode = "INVALID_ID";
        String errorMessage = "Invalid id";
        Error error = Error.builder().errorMessage(errorMessage).errorCode(errorCode)
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException(errorCode, errorMessage)).build();
        populateErrorDetails(idRecord, error, errorDetailsMap);
    }


}
