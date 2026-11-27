package org.egov.facility.validator;

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
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.FacilityBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.facility.config.FacilityConfiguration;
import org.egov.facility.web.models.boundary.BoundaryResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

/**
 * Validator class for validating facility boundaries.
 */
@Component
@Order(value = 4)
@Slf4j
public class FBoundaryValidator implements Validator<FacilityBulkRequest, Facility> {

    private final ServiceRequestClient serviceRequestClient;

    private final FacilityConfiguration facilityConfiguration;

    /**
     * Constructor to initialize the HBoundaryValidator.
     *
     * @param serviceRequestClient   Service request client for making HTTP requests
     * @param facilityConfiguration Configuration properties for the facility module
     */
    public FBoundaryValidator(ServiceRequestClient serviceRequestClient, FacilityConfiguration facilityConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.facilityConfiguration = facilityConfiguration;
    }

    /**
     * Validates the facilities' boundaries.
     *
     * @param request the bulk request containing facilities
     * @return a map containing facilities with their corresponding list of errors
     */
    @Override
    public Map<Facility, List<Error>> validate(FacilityBulkRequest request) {
        log.debug("Validating facilities boundaries.");
        // Create a HashMap to store error details for each facility
        HashMap<Facility, List<Error>> errorDetailsMap = new HashMap<>();

        // Filter facilities with non-null addresses
        List<Facility> entitiesWithValidBoundaries = request.getFacilities().parallelStream()
                .filter(facility -> Objects.nonNull(facility.getAddress()))
                .filter(facility -> Objects.nonNull(facility.getAddress().getLocality())) // Exclude null locality codes
                .collect(Collectors.toList());

        Map<String, List<Facility>> tenantIdFacilityMap = entitiesWithValidBoundaries.stream().collect(Collectors.groupingBy(Facility::getTenantId));

        tenantIdFacilityMap.forEach((tenantId, facilities) -> {
            // Group facilities by locality code
            Map<String, List<Facility>> boundaryCodeFacilitysMap = facilities.stream()
                    .collect(Collectors.groupingBy(
                            facility -> facility.getAddress().getLocality().getCode() // Group by locality code
                    ));

            List<String> boundaries = new ArrayList<>(boundaryCodeFacilitysMap.keySet());
            if(!CollectionUtils.isEmpty(boundaries)) {
                try {
                    // Fetch boundary details from the service
                    log.debug("Fetching boundary details for tenantId: {}, boundaries: {}", tenantId, boundaries);
                    BoundaryResponse boundarySearchResponse = serviceRequestClient.fetchResult(
                            new StringBuilder(facilityConfiguration.getBoundaryServiceHost()
                                    + facilityConfiguration.getBoundarySearchUrl()
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

                    // Filter out facilities with invalid boundary codes
                    List<Facility> facilitiesWithInvalidBoundaries = boundaryCodeFacilitysMap.entrySet().stream()
                            .filter(entry -> invalidBoundaryCodes.contains(entry.getKey())) // filter invalid boundary codes
                            .flatMap(entry -> entry.getValue().stream()) // Flatten the list of facilities
                            .collect(Collectors.toList());


                    facilitiesWithInvalidBoundaries.forEach(facility -> {
                        // Create an error object for facilities with invalid boundaries
                        Error error = Error.builder()
                                .errorMessage("Boundary code does not exist in db")
                                .errorCode("NON_EXISTENT_ENTITY")
                                .type(Error.ErrorType.NON_RECOVERABLE)
                                .exception(new CustomException("NON_EXISTENT_ENTITY", "Boundary code does not exist in db"))
                                .build();
                        // Populate error details for the facility
                        populateErrorDetails(facility, error, errorDetailsMap);
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
