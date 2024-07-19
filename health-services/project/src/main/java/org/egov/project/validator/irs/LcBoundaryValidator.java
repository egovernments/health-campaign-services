package org.egov.project.validator.irs;

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
import org.egov.common.models.project.irs.LocationCapture;
import org.egov.common.models.project.irs.LocationCaptureBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.web.models.boundary.BoundaryResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

/**
 * Validator class for validating locationCapture boundaries.
 */
@Component
@Order(value = 4)
@Slf4j
public class LcBoundaryValidator implements Validator<LocationCaptureBulkRequest, LocationCapture> {

    private final ServiceRequestClient serviceRequestClient;

    private final ProjectConfiguration projectConfiguration;

    /**
     * Constructor to initialize the HBoundaryValidator.
     *
     * @param serviceRequestClient   Service request client for making HTTP requests
     * @param projectConfiguration Configuration properties for the locationCapture module
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
    public Map<LocationCapture, List<Error>> validate(LocationCaptureBulkRequest request) {
        log.debug("Validating locationCaptures boundaries.");
        // Create a HashMap to store error details for each locationCapture
        HashMap<LocationCapture, List<Error>> errorDetailsMap = new HashMap<>();

        // Filter locationCaptures with non-null addresses
        List<LocationCapture> entitiesWithValidBoundaries = request.getLocationCaptures().parallelStream()
                .filter(locationCapture -> Objects.nonNull(locationCapture.getBoundaryCode())) // Exclude null boundary codes
                .collect(Collectors.toList());

        Map<String, List<LocationCapture>> tenantIdLocationCaptureMap = entitiesWithValidBoundaries.stream().collect(Collectors.groupingBy(LocationCapture::getTenantId));

        tenantIdLocationCaptureMap.forEach((tenantId, locationCaptures) -> {
            // Group locationCaptures by locality code
            Map<String, List<LocationCapture>> boundaryCodeLocationCapturesMap = locationCaptures.stream()
                    .collect(Collectors.groupingBy(
                            locationCapture -> locationCapture.getBoundaryCode() // Group by boundary code
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
                    List<LocationCapture> locationCapturesWithInvalidBoundaries = boundaryCodeLocationCapturesMap.entrySet().stream()
                            .filter(entry -> invalidBoundaryCodes.contains(entry.getKey())) // filter invalid boundary codes
                            .flatMap(entry -> entry.getValue().stream()) // Flatten the list of locationCaptures
                            .collect(Collectors.toList());


                    locationCapturesWithInvalidBoundaries.forEach(locationCapture -> {
                        // Create an error object for locationCaptures with invalid boundaries
                        Error error = Error.builder()
                                .errorMessage("Boundary code does not exist in db")
                                .errorCode("NON_EXISTENT_ENTITY")
                                .type(Error.ErrorType.NON_RECOVERABLE)
                                .exception(new CustomException("NON_EXISTENT_ENTITY", "Boundary code does not exist in db"))
                                .build();
                        // Populate error details for the locationCapture
                        populateErrorDetails(locationCapture, error, errorDetailsMap);
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
