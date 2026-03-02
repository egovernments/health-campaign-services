package org.egov.healthnotification.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.household.*;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.util.RequestInfoUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Service for interacting with the Household Service.
 * Used to fetch household and household member details for notification recipients.
 */
@Service
@Slf4j
public class HouseholdService {

    private final ServiceRequestClient serviceRequestClient;
    private final HealthNotificationProperties properties;

    @Autowired
    public HouseholdService(ServiceRequestClient serviceRequestClient,
                            HealthNotificationProperties properties) {
        this.serviceRequestClient = serviceRequestClient;
        this.properties = properties;
    }

    /**
     * Searches for a household by ID.
     *
     * @param householdId The household ID to search for
     * @param tenantId The tenant ID
     * @return The Household object
     */
    public Household searchHouseholdById(String householdId, String tenantId) {
        log.info("Searching household by ID: {} for tenant: {}", householdId, tenantId);

        HouseholdSearchRequest request = HouseholdSearchRequest.builder()
                .requestInfo(RequestInfoUtil.buildSystemRequestInfo())
                .household(HouseholdSearch.builder()
                        .id(Collections.singletonList(householdId))
                        .build())
                .build();

        try {
            StringBuilder uri = new StringBuilder();
            uri.append(properties.getHouseholdServiceHost())
                    .append(properties.getHouseholdSearchUrl())
                    .append("?limit=1")
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);

            HouseholdBulkResponse response = serviceRequestClient.fetchResult(uri, request,
                    HouseholdBulkResponse.class);

            if (response != null && response.getHouseholds() != null
                    && !response.getHouseholds().isEmpty()) {
                Household household = response.getHouseholds().get(0);
                log.info("Successfully fetched household: {}", householdId);
                return household;
            }

            log.error("Household not found for id: {}", householdId);
            throw new CustomException("HOUSEHOLD_NOT_FOUND",
                    String.format("Household not found for id: %s, tenantId: %s", householdId, tenantId));

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching household: {}", householdId, e);
            throw new CustomException("HOUSEHOLD_FETCH_ERROR",
                    "Error while fetching household details for id: " + householdId);
        }
    }

    /**
     * Searches for household members by household ID.
     *
     * @param householdId The household ID
     * @param tenantId The tenant ID
     * @return The HouseholdMember who is the head
     */
    public HouseholdMember searchHouseholdHead(String householdId, String tenantId) {
        log.info("Searching household head for household: {} in tenant: {}", householdId, tenantId);

        HouseholdMemberSearchRequest request = HouseholdMemberSearchRequest.builder()
                .requestInfo(RequestInfoUtil.buildSystemRequestInfo())
                .householdMemberSearch(HouseholdMemberSearch.builder()
                        .householdId(Collections.singletonList(householdId))
                        .isHeadOfHousehold(true)
                        .build())
                .build();

        try {
            StringBuilder uri = new StringBuilder();
            uri.append(properties.getHouseholdServiceHost())
                    .append(properties.getHouseholdMemberSearchUrl())
                    .append("?limit=1")
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);

            HouseholdMemberBulkResponse response = serviceRequestClient.fetchResult(uri, request,
                    HouseholdMemberBulkResponse.class);

            if (response != null && response.getHouseholdMembers() != null
                    && !response.getHouseholdMembers().isEmpty()) {
                HouseholdMember householdHead = response.getHouseholdMembers().get(0);
                log.info("Successfully fetched household head: {}, individualId: {}",
                        householdHead.getId(), householdHead.getIndividualId());
                return householdHead;
            }

            log.error("Household head not found for household: {}", householdId);
            throw new CustomException("HOUSEHOLD_HEAD_NOT_FOUND",
                    String.format("Household head not found for household: %s, tenantId: %s", householdId, tenantId));

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching household head for household: {}", householdId, e);
            throw new CustomException("HOUSEHOLD_HEAD_FETCH_ERROR",
                    "Error while fetching household head for household id: " + householdId);
        }
    }

    /**
     * Gets the individual ID of the household head.
     *
     * @param householdId The household ID
     * @param tenantId The tenant ID
     * @return Individual ID of household head, or null if not found
     */
    public String getHouseholdHeadIndividualId(String householdId, String tenantId) {
        HouseholdMember head = searchHouseholdHead(householdId, tenantId);
        if (head == null) {
            return null;
        }
        return head.getIndividualId();
    }

    /**
     * Searches for household head by household client reference ID.
     *
     * @param householdClientReferenceId The household client reference ID
     * @param tenantId The tenant ID
     * @return The HouseholdMember who is the head
     */
    public HouseholdMember searchHouseholdHeadByClientRefId(String householdClientReferenceId, String tenantId) {
        log.info("Searching household head by clientReferenceId: {} in tenant: {}",
                householdClientReferenceId, tenantId);

        HouseholdMemberSearchRequest request = HouseholdMemberSearchRequest.builder()
                .requestInfo(RequestInfoUtil.buildSystemRequestInfo())
                .householdMemberSearch(HouseholdMemberSearch.builder()
                        .householdClientReferenceId(Collections.singletonList(householdClientReferenceId))
                        .isHeadOfHousehold(true)
                        .build())
                .build();

        try {
            StringBuilder uri = new StringBuilder();
            uri.append(properties.getHouseholdServiceHost())
                    .append(properties.getHouseholdMemberSearchUrl())
                    .append("?limit=1")
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);

            HouseholdMemberBulkResponse response = serviceRequestClient.fetchResult(uri, request,
                    HouseholdMemberBulkResponse.class);

            if (response != null && response.getHouseholdMembers() != null
                    && !response.getHouseholdMembers().isEmpty()) {
                HouseholdMember householdHead = response.getHouseholdMembers().get(0);
                log.info("Successfully fetched household head: {}, individualId: {}",
                        householdHead.getId(), householdHead.getIndividualId());
                return householdHead;
            }

            log.error("Household head not found for beneficiaryClientRefId: {}", householdClientReferenceId);
            throw new CustomException("HOUSEHOLD_HEAD_NOT_FOUND",
                    String.format("Household head not found for beneficiaryClientRefId: %s, tenantId: %s",
                            householdClientReferenceId, tenantId));

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching household head for householdClientReferenceId: {}", householdClientReferenceId, e);
            throw new CustomException("HOUSEHOLD_HEAD_FETCH_ERROR",
                    "Error while fetching household head for householdClientReferenceId: " + householdClientReferenceId);
        }
    }
}
