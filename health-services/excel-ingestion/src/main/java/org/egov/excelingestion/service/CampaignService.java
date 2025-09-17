package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.egov.excelingestion.web.models.CampaignSearchResponse;
import org.egov.excelingestion.web.models.RequestInfo;
import org.egov.excelingestion.web.models.UserInfo;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for Campaign search operations with caching
 */
@Service
@Slf4j
public class CampaignService {

    private static final String CAMPAIGN_SEARCH_ENDPOINT = "/project-factory/v1/project-type/search";

    private final ServiceRequestRepository serviceRequestRepository;
    private final ExcelIngestionConfig config;
    private final ApiPayloadBuilder apiPayloadBuilder;
    private final CustomExceptionHandler exceptionHandler;

    public CampaignService(ServiceRequestRepository serviceRequestRepository,
                           ExcelIngestionConfig config,
                           ApiPayloadBuilder apiPayloadBuilder,
                           CustomExceptionHandler exceptionHandler) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
        this.apiPayloadBuilder = apiPayloadBuilder;
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Search campaign by ID with caching
     * Cache key is based on campaignId and tenantId for 15 minutes
     */
    @Cacheable(value = "campaignCache", key = "#campaignId + '_' + #tenantId", unless = "#result == null")
    public CampaignSearchResponse.CampaignDetail searchCampaignById(String campaignId, String tenantId, RequestInfo requestInfo) {
        // Ensure RequestInfo contains userInfo.tenantId expected by downstream services
        RequestInfo sanitizedRequestInfo = ensureTenantInRequestInfo(requestInfo, tenantId);

        log.info("Fetching campaign details for ID: {} in tenant: {}", campaignId, tenantId);

        try {
            // Build payload like other services (Boundary/MDMS)
            Map<String, Object> payload = apiPayloadBuilder.createCampaignSearchPayload(
                    sanitizedRequestInfo,
                    tenantId,
                    new String[]{campaignId},
                    true,
                    1,
                    0
            );

            String url = buildCampaignSearchUrl();
            StringBuilder uri = new StringBuilder(url);
            log.debug("Calling Campaign Search API: {}", url);

            CampaignSearchResponse response = serviceRequestRepository.fetchResult(
                    uri, payload, CampaignSearchResponse.class);

            if (response != null && response.getCampaignDetails() != null && !response.getCampaignDetails().isEmpty()) {
                CampaignSearchResponse.CampaignDetail campaign = response.getCampaignDetails().get(0);
                log.info("Successfully fetched campaign: {} with projectType: {} and {} boundaries",
                        campaign.getId(), campaign.getProjectType(),
                        campaign.getBoundaries() != null ? campaign.getBoundaries().size() : 0);
                return campaign;
            } else {
                log.error("Campaign not found with ID: {} in tenant: {}", campaignId, tenantId);
                exceptionHandler.throwCustomException(
                        ErrorConstants.CAMPAIGN_NOT_FOUND,
                        ErrorConstants.CAMPAIGN_NOT_FOUND_MESSAGE
                                .replace("{0}", campaignId)
                                .replace("{1}", tenantId)
                );
                return null; // never reached
            }
        } catch (org.egov.tracer.model.CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching campaign details for ID: {} in tenant: {}: {}",
                    campaignId, tenantId, e.getMessage(), e);
            exceptionHandler.throwCustomException(
                    ErrorConstants.CAMPAIGN_SERVICE_ERROR,
                    ErrorConstants.CAMPAIGN_SERVICE_ERROR_MESSAGE + ": " + e.getMessage(), e);
            return null; // never reached
        }
    }

    /**
     * Extract projectType from campaign with validation
     */
    public String getProjectTypeFromCampaign(String campaignId, String tenantId, RequestInfo requestInfo) {
        CampaignSearchResponse.CampaignDetail campaign = searchCampaignById(campaignId, tenantId, requestInfo);

        if (campaign != null) {
            String projectType = campaign.getProjectType();
            if (projectType == null || projectType.trim().isEmpty()) {
                log.error("Campaign found but projectType is missing for campaign ID: {}", campaignId);
                exceptionHandler.throwCustomException(
                        ErrorConstants.CAMPAIGN_DATA_INCOMPLETE,
                        ErrorConstants.CAMPAIGN_DATA_INCOMPLETE_MESSAGE.replace("{0}", campaignId)
                );
            }
            return projectType;
        }
        return null; // never reached
    }

    /**
     * Extract boundaries from campaign with validation
     */
    public java.util.List<CampaignSearchResponse.BoundaryDetail> getBoundariesFromCampaign(String campaignId, String tenantId, RequestInfo requestInfo) {
        CampaignSearchResponse.CampaignDetail campaign = searchCampaignById(campaignId, tenantId, requestInfo);
        if (campaign != null) {
            return campaign.getBoundaries();
        }
        return null; // never reached
    }

    /**
     * Search campaign data for existing users by phone numbers
     * Returns List of campaign data records that match the phone numbers with status "completed"
     */
    public java.util.List<Map<String, Object>> searchCampaignDataByPhoneNumbers(
            java.util.List<String> phoneNumbers, String campaignNumber, String tenantId, RequestInfo requestInfo) {
        
        RequestInfo sanitizedRequestInfo = ensureTenantInRequestInfo(requestInfo, tenantId);
        log.info("Searching campaign data for {} phone numbers in campaign: {} tenant: {}", 
                phoneNumbers.size(), campaignNumber, tenantId);
        
        try {
            // Build payload for campaign data search
            Map<String, Object> payload = Map.of(
                "RequestInfo", sanitizedRequestInfo,
                "SearchCriteria", Map.of(
                    "tenantId", tenantId,
                    "type", "user",
                    "campaignNumber", campaignNumber,
                    "status", "completed",
                    "uniqueIdentifiers", phoneNumbers
                )
            );
            
            String url = config.getCampaignDataSearchUrl();
            log.debug("Calling Campaign Data Search API: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = serviceRequestRepository.fetchResult(
                    new StringBuilder(url), payload, Map.class);
            
            if (response != null && response.get("CampaignData") != null) {
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> campaignData = 
                    (java.util.List<Map<String, Object>>) response.get("CampaignData");
                
                log.info("Found {} existing users in campaign data with completed status", campaignData.size());
                return campaignData;
            } else {
                log.info("No existing users found in campaign data");
                return new java.util.ArrayList<>();
            }
            
        } catch (Exception e) {
            log.error("Error searching campaign data for phone numbers: {}", e.getMessage(), e);
            // Return empty list instead of throwing exception to allow processing to continue
            return new java.util.ArrayList<>();
        }
    }

    /**
     * Build campaign search URL from configuration
     */
    private String buildCampaignSearchUrl() {
        String baseUrl = config.getCampaignHost();
        return baseUrl + CAMPAIGN_SEARCH_ENDPOINT;
    }

    /**
     * Ensures that RequestInfo contains userInfo with tenantId populated.
     * If missing, it creates or updates the userInfo with the provided tenantId.
     */
    private RequestInfo ensureTenantInRequestInfo(RequestInfo requestInfo, String tenantId) {
        if (requestInfo == null) {
            return RequestInfo.builder()
                    .userInfo(UserInfo.builder().tenantId(tenantId).build())
                    .build();
        }
        UserInfo user = requestInfo.getUserInfo();
        if (user == null) {
            user = UserInfo.builder().tenantId(tenantId).build();
            requestInfo.setUserInfo(user);
            return requestInfo;
        }
        if (user.getTenantId() == null || user.getTenantId().isBlank()) {
            user.setTenantId(tenantId);
        }
        return requestInfo;
    }
}
