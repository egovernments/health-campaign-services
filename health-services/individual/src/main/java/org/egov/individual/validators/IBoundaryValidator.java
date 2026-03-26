package org.egov.individual.validators;

import java.util.AbstractMap;
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
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.web.models.boundary.BoundaryResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

/**
 * Validator class for validating individual boundaries.
 */
@Component
@Order(value = 4)
@Slf4j
public class IBoundaryValidator implements Validator<IndividualBulkRequest, Individual> {

    private final ServiceRequestClient serviceRequestClient;

    private final IndividualProperties individualProperties;

    /**
     * Constructor to initialize the IBoundaryValidator.
     *
     * @param serviceRequestClient   Service request client for making HTTP requests
     * @param individualProperties Configuration properties for the individual module
     */
    public IBoundaryValidator(ServiceRequestClient serviceRequestClient, IndividualProperties individualProperties) {
        this.serviceRequestClient = serviceRequestClient;
        this.individualProperties = individualProperties;
    }

    /**
     * Validates the individuals' boundaries.
     *
     * @param request the bulk request containing individuals
     * @return a map containing individuals with their corresponding list of errors
     */
    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        log.debug("Validating individuals boundaries.");
        // Create a HashMap to store error details for each individual
        HashMap<Individual, List<Error>> errorDetailsMap = new HashMap<>();

        // Filter individuals with non-null addresses
        List<Individual> entitiesWithValidBoundaries = request.getIndividuals().parallelStream()
                .filter(individual -> !CollectionUtils.isEmpty(individual.getAddress()))
                .collect(Collectors.toList());

        Map<String, List<Individual>> tenantIdIndividualMap = entitiesWithValidBoundaries.stream().collect(Collectors.groupingBy(Individual::getTenantId));

        tenantIdIndividualMap.forEach((tenantId, individuals) -> {
            // Group individuals by locality code
            Map<String, List<Individual>> boundaryCodeIndividualsMap = individuals.stream()
                    .flatMap(individual -> individual.getAddress().stream()
                            .filter(address -> Objects.nonNull(address.getLocality())) // Filter out addresses with null locality
                            .filter(address -> Objects.nonNull(address.getLocality().getCode())) // Filter out addresses with null locality code
                            .filter(address -> !address.getLocality().getCode().trim().isEmpty()) // Filter out empty locality codes
                            .map(address -> new AbstractMap.SimpleEntry<>(address.getLocality().getCode(), individual))) // Map each address to its locality code and individual
                    .collect(Collectors.groupingBy(Map.Entry::getKey, // Group by locality code
                            Collectors.mapping(Map.Entry::getValue, Collectors.toList()))); // Collect individuals into a list for each locality code

            List<String> boundaries = new ArrayList<>(boundaryCodeIndividualsMap.keySet());
            if(!CollectionUtils.isEmpty(boundaries)) {
                try {
                    // Fetch boundary details from the service
                    log.debug("Fetching boundary details for tenantId: {}, boundaries: {}", tenantId, boundaries);
                    BoundaryResponse boundarySearchResponse = serviceRequestClient.fetchResult(
                            new StringBuilder(individualProperties.getBoundaryServiceHost()
                                    + individualProperties.getBoundarySearchUrl()
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

                    // Filter out individuals with invalid boundary codes
                    List<Individual> individualsWithInvalidBoundaries = boundaryCodeIndividualsMap.entrySet().stream()
                            .filter(entry -> invalidBoundaryCodes.contains(entry.getKey())) // filter invalid boundary codes
                            .flatMap(entry -> entry.getValue().stream()) // Flatten the list of individuals
                            .collect(Collectors.toList());


                    individualsWithInvalidBoundaries.forEach(individual -> {
                        // Create an error object for individuals with invalid boundaries
                        Error error = Error.builder()
                                .errorMessage("Boundary code does not exist in db")
                                .errorCode("NON_EXISTENT_ENTITY")
                                .type(Error.ErrorType.NON_RECOVERABLE)
                                .exception(new CustomException("NON_EXISTENT_ENTITY", "Boundary code does not exist in db"))
                                .build();
                        // Populate error details for the individual
                        populateErrorDetails(individual, error, errorDetailsMap);
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
