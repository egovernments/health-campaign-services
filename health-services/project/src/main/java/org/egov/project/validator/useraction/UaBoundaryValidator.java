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
 * Validator class for validating userAction boundaries.
 */
@Component
@Order(value = 4)
@Slf4j
public class UaBoundaryValidator implements Validator<UserActionBulkRequest, UserAction> {

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectConfiguration projectConfiguration;

    /**
     * Constructor to initialize the HBoundaryValidator.
     *
     * @param serviceRequestClient   Service request client for making HTTP requests
     * @param projectConfiguration Configuration properties for the userAction module
     */
    public UaBoundaryValidator(ServiceRequestClient serviceRequestClient, ProjectConfiguration projectConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.projectConfiguration = projectConfiguration;
    }

    /**
     * Validates the userActions' boundaries.
     *
     * @param request the bulk request containing userActions
     * @return a map containing userActions with their corresponding list of errors
     */
    @Override
    public Map<UserAction, List<Error>> validate(UserActionBulkRequest request) {
        log.debug("Validating userActions boundaries.");
        // Create a HashMap to store error details for each userAction
        HashMap<UserAction, List<Error>> errorDetailsMap = new HashMap<>();

        // Filter userActions with non-null addresses
        List<UserAction> entitiesWithValidBoundaries = request.getUserActions().parallelStream()
                .filter(userAction -> Objects.nonNull(userAction.getBoundaryCode())) // Exclude null boundary codes
                .collect(Collectors.toList());

        Map<String, List<UserAction>> tenantIdUserActionMap = entitiesWithValidBoundaries.stream().collect(Collectors.groupingBy(UserAction::getTenantId));

        tenantIdUserActionMap.forEach((tenantId, userActions) -> {
            // Group userActions by locality code
            Map<String, List<UserAction>> boundaryCodeUserActionsMap = userActions.stream()
                    .collect(Collectors.groupingBy(
                            userAction -> userAction.getBoundaryCode() // Group by boundary code
                    ));

            List<String> boundaries = new ArrayList<>(boundaryCodeUserActionsMap.keySet());
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

                    // Filter out userActions with invalid boundary codes
                    List<UserAction> userActionsWithInvalidBoundaries = boundaryCodeUserActionsMap.entrySet().stream()
                            .filter(entry -> invalidBoundaryCodes.contains(entry.getKey())) // filter invalid boundary codes
                            .flatMap(entry -> entry.getValue().stream()) // Flatten the list of userActions
                            .collect(Collectors.toList());


                    userActionsWithInvalidBoundaries.forEach(userAction -> {
                        // Create an error object for userActions with invalid boundaries
                        Error error = Error.builder()
                                .errorMessage("Boundary code does not exist in db")
                                .errorCode("NON_EXISTENT_ENTITY")
                                .type(Error.ErrorType.NON_RECOVERABLE)
                                .exception(new CustomException("NON_EXISTENT_ENTITY", "Boundary code does not exist in db"))
                                .build();
                        // Populate error details for the userAction
                        populateErrorDetails(userAction, error, errorDetailsMap);
                    });

                } catch (Exception e) {
                    log.error("Exception while searching boundaries for tenantId: {}", tenantId, e);
                    // Throw a custom exception if an error occurs during boundary search
                    throw new CustomException("BOUNDARY_SERVICE_SEARCH_ERROR","Error in while fetching boundaries from Boundary Service : " + e.getMessage());
                }
            }
        });

        return errorDetailsMap;
    }
}
