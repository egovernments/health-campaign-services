package org.egov.project.validator.useraction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.core.Boundary;
import org.egov.common.models.project.useraction.UserAction;
import org.egov.common.models.project.useraction.UserActionBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.web.models.boundary.BoundaryResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

/**
 * Validator class for validating locationCaptureUserAction boundaries.
 */
@Component
@Order(value = 4)
@Slf4j
public class LcBoundaryValidator implements Validator<UserActionBulkRequest, UserAction> {

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectConfiguration projectConfiguration;

    /**
     * Constructor to initialize the HBoundaryValidator.
     *
     * @param serviceRequestClient   Service request client for making HTTP requests
     * @param projectConfiguration Configuration properties for the locationCaptureUserAction module
     */
    public LcBoundaryValidator(ServiceRequestClient serviceRequestClient, ProjectConfiguration projectConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.projectConfiguration = projectConfiguration;
    }

    /**
     * Validates the locationCaptures' boundaries.
     *
     * @param request the bulk request containing locationCaptures
     * @return a map containing locationCaptures with their corresponding list of errors
     */
    @Override
    public Map<UserAction, List<Error>> validate(UserActionBulkRequest request) {
        log.debug("Validating locationCaptures boundaries.");
        // Create a HashMap to store error details for each locationCaptureUserAction
        HashMap<UserAction, List<Error>> errorDetailsMap = new HashMap<>();

        // Filter locationCaptures with non-null addresses
        List<UserAction> entitiesWithValidBoundaries = request.getUserActions().parallelStream()
                .filter(locationCaptureUserAction -> Objects.nonNull(locationCaptureUserAction.getBoundaryCode())) // Exclude null boundary codes
                .collect(Collectors.toList());

        Map<String, List<UserAction>> tenantIdLocationCaptureMap = entitiesWithValidBoundaries.stream().collect(Collectors.groupingBy(UserAction::getTenantId));

        tenantIdLocationCaptureMap.forEach((tenantId, locationCaptures) -> {
            // Group locationCaptures by locality code
            Map<String, List<UserAction>> boundaryCodeLocationCapturesMap = locationCaptures.stream()
                    .collect(Collectors.groupingBy(
                            locationCaptureUserAction -> locationCaptureUserAction.getBoundaryCode() // Group by boundary code
                    ));

            List<String> boundaries = new ArrayList<>(boundaryCodeLocationCapturesMap.keySet());
            if(!CollectionUtils.isEmpty(boundaries)) {
                try {
                    // Fetch boundary details from the service
                    log.debug("Fetching boundary details for tenantId: {}, boundaries: {}", tenantId, boundaries);
                    BoundaryResponse boundarySearchResponse = serviceRequestClient.fetchResult(
                            new StringBuilder(projectConfiguration.getBoundaryServiceHost()
                                    + projectConfiguration.getBoundarySearchUrl()
                                    +"?limit=" + boundaries.size()
                                    + "&offset=0&tenantId=" + tenantId
                                    + "&codes=" + String.join(",", boundaries)),
                            request.getRequestInfo(),
                            BoundaryResponse.class
                    );
                    log.debug("Boundary details fetched successfully for tenantId: {}", tenantId);

                    List<String> invalidBoundaryCodes = new ArrayList<>(boundaries);
                    invalidBoundaryCodes.removeAll(boundarySearchResponse.getBoundary().stream()
                            .map(Boundary::getCode)
                            .collect(Collectors.toList())
                    );

                    // Filter out locationCaptures with invalid boundary codes
                    List<UserAction> locationCapturesWithInvalidBoundaries = boundaryCodeLocationCapturesMap.entrySet().stream()
                            .filter(entry -> invalidBoundaryCodes.contains(entry.getKey())) // filter invalid boundary codes
                            .flatMap(entry -> entry.getValue().stream()) // Flatten the list of locationCaptures
                            .collect(Collectors.toList());

                    // Create an error object for locationCaptures with invalid boundaries
                    Error error = Error.builder()
                            .errorMessage("Boundary code does not exist in db")
                            .errorCode("NON_EXISTENT_ENTITY")
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException("NON_EXISTENT_ENTITY", "Boundary code does not exist in db"))
                            .build();
                    locationCapturesWithInvalidBoundaries.forEach(locationCaptureUserAction -> {
                        // Populate error details for the locationCaptureUserAction
                        populateErrorDetails(locationCaptureUserAction, error, errorDetailsMap);
                    });

                } catch (Exception e) {
                    log.error("Exception while searching boundaries for tenantId: {}, boundaries: {}", tenantId, boundaries, e);
                    // Throw a custom exception if an error occurs during boundary search
                    throw new CustomException("BOUNDARY_SERVICE_SEARCH_ERROR",
                            "Error fetching boundaries from Boundary Service for tenantId: " + tenantId + ", boundaries: " + boundaries + " : " + e.getMessage()
                    );
                }
            }
        });

        return errorDetailsMap;
    }
}
