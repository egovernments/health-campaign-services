package org.egov.project.validator.useraction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.common.models.project.useraction.UserActionBulkRequest;
import org.egov.common.models.project.useraction.UserActionSearch;
import org.egov.common.validator.Validator;
import org.egov.project.repository.LocationCaptureRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

@Component
@Order(value = 1)
@Slf4j
public class LcExistentEntityValidator implements Validator<UserActionBulkRequest, UserAction> {
    private LocationCaptureRepository locationCaptureRepository;

    @Autowired
    public LcExistentEntityValidator(LocationCaptureRepository locationCaptureRepository) {
        this.locationCaptureRepository = locationCaptureRepository;
    }

    /**
     * @param request
     * @return
     */
    @Override
    public Map<UserAction, List<Error>> validate(UserActionBulkRequest request) {
        // Map to hold LocationCapture entities and their error details
        Map<UserAction, List<Error>> errorDetailsMap = new HashMap<>();
        // Get the list of LocationCapture entities from the request
        List<UserAction> entities = request.getUserActions();
        // Extract client reference IDs from LocationCapture entities without errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors())
                .map(UserAction::getClientReferenceId)
                .collect(Collectors.toList());
        // Create a search object for querying entities by client reference IDs
        UserActionSearch locationCaptureSearch = UserActionSearch.builder()
                .clientReferenceId(clientReferenceIdList)
                .build();
        // Check if the client reference ID list is not empty
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities by client reference IDs
            List<UserAction> existentEntities = locationCaptureRepository.findById(
                    clientReferenceIdList,
                    getIdFieldName(locationCaptureSearch)
            ).getResponse();
            // For each existing entity, populate error details for uniqueness
            existentEntities.forEach(entity -> {
                Error error = getErrorForUniqueEntity();
                populateErrorDetails(entity, error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }
}
