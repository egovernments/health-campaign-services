package org.egov.project.validator.irs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.irs.LocationCapture;
import org.egov.common.models.project.irs.LocationCaptureBulkRequest;
import org.egov.common.models.project.irs.LocationCaptureSearch;
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
public class LcExistentEntityValidator implements Validator<LocationCaptureBulkRequest, LocationCapture> {
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
    public Map<LocationCapture, List<Error>> validate(LocationCaptureBulkRequest request) {
        // Map to hold LocationCapture entities and their error details
        Map<LocationCapture, List<Error>> errorDetailsMap = new HashMap<>();
        // Get the list of LocationCapture entities from the request
        List<LocationCapture> entities = request.getLocationCaptures();
        // Extract client reference IDs from LocationCapture entities without errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors())
                .map(LocationCapture::getClientReferenceId)
                .collect(Collectors.toList());
        // Create a search object for querying entities by client reference IDs
        LocationCaptureSearch locationCaptureSearch = LocationCaptureSearch.builder()
                .clientReferenceId(clientReferenceIdList)
                .build();
        // Check if the client reference ID list is not empty
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities by client reference IDs
            List<LocationCapture> existentEntities = locationCaptureRepository.findById(
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
