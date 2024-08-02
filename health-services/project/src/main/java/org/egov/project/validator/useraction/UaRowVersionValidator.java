package org.egov.project.validator.useraction;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.common.models.project.useraction.UserActionBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.repository.UserActionRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.getEntitiesWithMismatchedRowVersion;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForRowVersionMismatch;

@Component
@Order(value = 5)
@Slf4j
public class UaRowVersionValidator implements Validator<UserActionBulkRequest, UserAction> {

    private final UserActionRepository userActionRepository;

    @Autowired
    public UaRowVersionValidator(UserActionRepository userActionRepository) {
        this.userActionRepository = userActionRepository;
    }


    @Override
    public Map<UserAction, List<Error>> validate(UserActionBulkRequest request) {
        log.info("validating row version");
        Map<UserAction, List<Error>> errorDetailsMap = new HashMap<>();
        try {

            Method idMethod = getIdMethod(request.getUserActions());
            Map<String, UserAction> eMap = getIdToObjMap(request.getUserActions().stream()
                    .filter(notHavingErrors())
                    .collect(Collectors.toList()), idMethod);
            if (!eMap.isEmpty()) {
                List<String> entityIds = new ArrayList<>(eMap.keySet());
                List<UserAction> existingEntities = userActionRepository.findById(entityIds,
                        getIdFieldName(idMethod)).getResponse();
                List<UserAction> entitiesWithMismatchedRowVersion =
                        getEntitiesWithMismatchedRowVersion(eMap, existingEntities, idMethod);
                entitiesWithMismatchedRowVersion.forEach(individual -> {
                    Error error = getErrorForRowVersionMismatch();
                    populateErrorDetails(individual, error, errorDetailsMap);
                });
            }
        } catch (Exception e) {
            log.error("Exception occurred during validation: {}", e.getMessage());
            throw new CustomException("VALIDATION_ERROR", "Error occurred while validating project IDs"+e);
        }
        return errorDetailsMap;
    }
}
