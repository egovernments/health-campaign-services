package org.egov.household.validators.household;

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
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.household.config.HouseholdConfiguration;
import org.egov.household.web.models.boundary.BoundaryResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

@Component
@Order(value = 4)
@Slf4j
public class HBoundaryValidator implements Validator<HouseholdBulkRequest, Household> {

    private final ServiceRequestClient serviceRequestClient;

    private final HouseholdConfiguration householdConfiguration;

    public HBoundaryValidator(ServiceRequestClient serviceRequestClient, HouseholdConfiguration householdConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.householdConfiguration = householdConfiguration;
    }

    /**
     * @param request
     * @return
     */
    @Override
    public Map<Household, List<Error>> validate(HouseholdBulkRequest request) {
        // Create a HashMap to store error details for each household
        HashMap<Household, List<Error>> errorDetailsMap = new HashMap<>();

        // Filter households with non-null addresses
        List<Household> entitiesWithValidBoundaries = request.getHouseholds().parallelStream()
                .filter(household -> Objects.nonNull(household.getAddress()))
                .filter(household -> Objects.nonNull(household.getAddress().getLocality())) // Exclude null locality codes
                .collect(Collectors.toList());

        Map<String, List<Household>> tenantIdHouseholdMap = entitiesWithValidBoundaries.stream().collect(Collectors.groupingBy(Household::getTenantId));

        tenantIdHouseholdMap.forEach((tenantId, households) -> {
            // Group households by locality code
            Map<String, List<Household>> boundaryCodeHouseholdsMap = households.stream()
                    .collect(Collectors.groupingBy(
                            household -> household.getAddress().getLocality().getCode() // Group by locality code
                    ));

            List<String> boundaries = new ArrayList<>(boundaryCodeHouseholdsMap.keySet());
            try {
                BoundaryResponse boundarySearchResponse = serviceRequestClient.fetchResult(
                        new StringBuilder(householdConfiguration.getBoundaryServiceHost()
                                + householdConfiguration.getBoundarySearchUrl()
                                +"?limit=" + boundaries.size()
                                + "&offset=0&tenantId=" + tenantId
                                + "&codes=" + String.join(",", boundaries)),
                        request.getRequestInfo(),
                        BoundaryResponse.class
                );
                List<String> invalidBoundaryCodes = new ArrayList<>(boundaries);
                invalidBoundaryCodes.removeAll(boundarySearchResponse.getBoundary().stream()
                        .map(Boundary::getCode)
                        .collect(Collectors.toList())
                );

                // Filter out households with invalid boundary codes
                List<Household> householdsWithInvalidBoundaries = boundaryCodeHouseholdsMap.entrySet().stream()
                        .filter(entry -> invalidBoundaryCodes.contains(entry.getKey())) // filter invalid boundary codes
                        .flatMap(entry -> entry.getValue().stream()) // Flatten the list of households
                        .collect(Collectors.toList());


                householdsWithInvalidBoundaries.forEach(household -> {
                    Error error = Error.builder()
                            .errorMessage("Boundary code does not exist in db")
                            .errorCode("NON_EXISTENT_ENTITY")
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException("NON_EXISTENT_ENTITY", "Boundary code does not exist in db"))
                            .build();
                    populateErrorDetails(household, error, errorDetailsMap);
                });

            } catch (Exception e) {
                log.error("Exception while searching boundaries : ", e);
                throw new CustomException("BOUNDARY_SEARCH_ERROR", e.getMessage());
            }
        });

        return errorDetailsMap;

    }
}
