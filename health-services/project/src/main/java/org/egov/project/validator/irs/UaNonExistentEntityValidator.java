package org.egov.project.validator.irs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.core.URLParams;
import org.egov.common.models.project.irs.UserAction;
import org.egov.common.models.project.irs.UserActionBulkRequest;
import org.egov.common.models.project.irs.UserActionSearch;
import org.egov.common.validator.Validator;
import org.egov.project.repository.UserActionRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.checkNonExistentEntities;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.project.Constants.GET_ID;

@Component
@Order(value = 4)
@Slf4j
public class UaNonExistentEntityValidator implements Validator<UserActionBulkRequest, UserAction> {

    private final UserActionRepository userActionRepository;

    @Autowired
    public UaNonExistentEntityValidator(UserActionRepository userActionRepository) {
        this.userActionRepository = userActionRepository;
    }


    @Override
    public Map<UserAction, List<Error>> validate(UserActionBulkRequest request) {
        log.info("validating for existence of entity");
        Map<UserAction, List<Error>> errorDetailsMap = new HashMap<>();
        List<UserAction> entities = request.getUserActions();
        Class<?> objClass = getObjClass(entities);
        Method idMethod = getMethod(GET_ID, objClass);
        Map<String, UserAction> eMap = getIdToObjMap(entities
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        // Lists to store IDs and client reference IDs
        List<String> idList = new ArrayList<>();
        List<String> clientReferenceIdList = new ArrayList<>();
        // Extract IDs and client reference IDs from Project UserAction entities
        entities.forEach(entity -> {
            idList.add(entity.getId());
            clientReferenceIdList.add(entity.getClientReferenceId());
        });
        if (!eMap.isEmpty()) {
            UserActionSearch taskSearch = UserActionSearch.builder()
                    .clientReferenceId(clientReferenceIdList)
                    .id(idList)
                    .build();

            URLParams urlParams = URLParams.builder()
                    .tenantId(entities.get(0).getTenantId())
                    .limit(entities.size())
                    .offset(0)
                    .includeDeleted(false)
                    .lastChangedSince(null)
                    .build();

            List<UserAction> existingEntities;
            try {
                // Query the repository to find existing entities
                existingEntities = userActionRepository.find(taskSearch, urlParams).getResponse();
            } catch (Exception e) {
                // Handle query builder exception
                log.error("Search failed for ProjectUserAction with error: {}", e.getMessage(), e);
                throw new CustomException("SEARCH_FAILED", "Search Failed for given ProjectUserAction, " + e.getMessage());
            }
            List<UserAction> nonExistentEntities = checkNonExistentEntities(eMap,
                    existingEntities, idMethod);
            nonExistentEntities.forEach(task -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(task, error, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }
}
