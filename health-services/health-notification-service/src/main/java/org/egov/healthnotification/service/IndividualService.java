package org.egov.healthnotification.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.individual.IndividualSearchRequest;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.util.RequestInfoUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Service for interacting with the Individual Service.
 * Used to fetch individual (health worker, beneficiary) details for notifications.
 */
@Service
@Slf4j
public class IndividualService {

    private final ServiceRequestClient serviceRequestClient;
    private final HealthNotificationProperties properties;

    @Autowired
    public IndividualService(ServiceRequestClient serviceRequestClient,
                             HealthNotificationProperties properties) {
        this.serviceRequestClient = serviceRequestClient;
        this.properties = properties;
    }

    /**
     * Searches for an individual by ID.
     *
     * @param individualId The individual ID to search for
     * @param tenantId The tenant ID
     * @return The Individual object, or null if not found
     */
    public Individual searchIndividualById(String individualId, String tenantId) {
        log.info("Searching individual by ID: {} for tenant: {}", individualId, tenantId);

        IndividualSearchRequest request = IndividualSearchRequest.builder()
                .requestInfo(RequestInfoUtil.buildSystemRequestInfo())
                .individual(IndividualSearch.builder()
                        .id(Collections.singletonList(individualId))
                        .build())
                .build();

        try {
            StringBuilder uri = new StringBuilder();
            uri.append(properties.getIndividualServiceHost())
                    .append(properties.getIndividualSearchUrl())
                    .append("?limit=1")
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);

            IndividualBulkResponse response = serviceRequestClient.fetchResult(
                    uri,
                    request,
                    IndividualBulkResponse.class);

            if (response != null && response.getIndividual() != null
                    && !response.getIndividual().isEmpty()) {
                log.info("Found individual: {}", individualId);
                return response.getIndividual().get(0);
            }

            log.warn("Individual not found: {}", individualId);
            return null;

        } catch (Exception e) {
            log.error("Error fetching individual: {}", individualId, e);
            throw new CustomException("INDIVIDUAL_FETCH_ERROR",
                    "Error while fetching individual details for id: " + individualId);
        }
    }

    /**
     * Gets mobile number from an individual.
     *
     * @param individualId The individual ID
     * @param tenantId The tenant ID
     * @return Mobile number of the individual, or null if not found
     */
    public String getIndividualMobile(String individualId, String tenantId) {
        Individual individual = searchIndividualById(individualId, tenantId);
        if (individual == null) {
            return null;
        }

        return individual.getMobileNumber();
    }

    /**
     * Gets individual name.
     *
     * @param individual The Individual object
     * @return Full name of the individual
     */
    public String getIndividualName(Individual individual) {
        if (individual == null || individual.getName() == null) {
            return null;
        }

        return individual.getName().getGivenName()
                + (individual.getName().getFamilyName() != null
                ? " " + individual.getName().getFamilyName()
                : "");
    }
}
