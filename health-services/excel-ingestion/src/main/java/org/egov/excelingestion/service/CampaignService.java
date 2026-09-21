package org.egov.excelingestion.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.config.ExcelIngestionConfig;
import org.egov.excelingestion.exception.CustomExceptionHandler;
import org.egov.excelingestion.repository.ServiceRequestRepository;
import org.egov.excelingestion.web.models.CampaignSearchResponse;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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
     * Search campaign by ID with caching. Cached by campaignId + tenantId (requestInfo is auth-only and
     * does not affect the result), so repeated lookups of the same campaign within the TTL reuse one call.
     */
    @Cacheable(value = "campaignDetail", key = "#campaignId + '_' + #tenantId")
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
     * Extract projectType from campaign with validation. Cached at its own level (by campaignId +
     * tenantId): the internal searchCampaignById call is a same-bean invocation so it is not served from
     * the campaignDetail cache, but caching here means repeat calls for the same campaign skip the work.
     */
    @Cacheable(value = "campaignProjectType", key = "#campaignId + '_' + #tenantId")
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
     * Extract boundaries from campaign with validation. Cached by campaignId + tenantId. This is the
     * hot upload-path entry point (called by the user + facility processors and BoundaryUtil), so caching
     * here collapses those repeated per-upload campaign fetches into one. The returned list is treated as
     * read-only by callers (consistent with the other cached collections in this service).
     */
    @Cacheable(value = "campaignBoundaries", key = "#campaignId + '_' + #tenantId", unless = "#result == null")
    public java.util.List<CampaignSearchResponse.BoundaryDetail> getBoundariesFromCampaign(String campaignId, String tenantId, RequestInfo requestInfo) {
        CampaignSearchResponse.CampaignDetail campaign = searchCampaignById(campaignId, tenantId, requestInfo);
        if (campaign != null) {
            return campaign.getBoundaries();
        }
        return null; // never reached
    }

    /**
     * Generic method to search campaign data by unique identifiers
     * Returns List of campaign data records that match the criteria
     * 
     * @param uniqueIdentifiers List of unique identifiers to search for
     * @param type Type of data (e.g., "user", "facility", "boundary")
     * @param status Status to filter by (e.g., "completed", "pending", "failed") - optional
     * @param campaignNumber Campaign number to filter by - optional (null for all campaigns)
     * @param tenantId Tenant ID
     * @param requestInfo Request info
     * @return List of matching campaign data records
     */
    public java.util.List<Map<String, Object>> searchCampaignDataByUniqueIdentifiers(
            java.util.List<String> uniqueIdentifiers, String type, String status, 
            String campaignNumber, String tenantId, RequestInfo requestInfo) {
        
        RequestInfo sanitizedRequestInfo = ensureTenantInRequestInfo(requestInfo, tenantId);
        
        if (campaignNumber != null) {
            log.info("Searching campaign data for {} {} identifiers in campaign: {} with status: {} in tenant: {}", 
                    uniqueIdentifiers.size(), type, campaignNumber, status, tenantId);
        } else {
            log.info("Searching campaign data for {} {} identifiers across all campaigns with status: {} in tenant: {}", 
                    uniqueIdentifiers.size(), type, status, tenantId);
        }
        
        try {
            // Build search criteria
            Map<String, Object> searchCriteria = new HashMap<>();
            searchCriteria.put("tenantId", tenantId);
            searchCriteria.put("type", type);
            searchCriteria.put("uniqueIdentifiers", uniqueIdentifiers);
            
            // Add optional criteria
            if (status != null && !status.trim().isEmpty()) {
                searchCriteria.put("status", status);
            }
            if (campaignNumber != null && !campaignNumber.trim().isEmpty()) {
                searchCriteria.put("campaignNumber", campaignNumber);
            }
            
            // Build payload for campaign data search
            Map<String, Object> payload = Map.of(
                "RequestInfo", sanitizedRequestInfo,
                "SearchCriteria", searchCriteria
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
                
                log.info("Found {} existing {} records in campaign data", campaignData.size(), type);
                return campaignData;
            } else {
                log.info("No existing {} records found in campaign data", type);
                return new java.util.ArrayList<>();
            }
            
        } catch (Exception e) {
            log.error("Error searching campaign data for {} identifiers: {}", type, e.getMessage(), e);
            exceptionHandler.throwCustomException(
                    ErrorConstants.CAMPAIGN_DATA_SEARCH_ERROR,
                    ErrorConstants.CAMPAIGN_DATA_SEARCH_ERROR_MESSAGE + ": " + e.getMessage(), e);
            return null; // never reached
        }
    }

    /**
     * Search campaign data by type, status, and campaignNumber (no uniqueIdentifiers required).
     * Supports pagination to collect all matching records.
     *
     * @param type Type of data (e.g., "user", "facility")
     * @param status Status to filter by (e.g., "completed")
     * @param campaignNumber Campaign number to filter by
     * @param tenantId Tenant ID
     * @param requestInfo Request info
     * @return List of all matching campaign data records
     */
    public java.util.List<Map<String, Object>> searchCampaignDataByType(
            String type, String status, String campaignNumber,
            String tenantId, RequestInfo requestInfo) {

        RequestInfo sanitizedRequestInfo = ensureTenantInRequestInfo(requestInfo, tenantId);

        log.info("Searching campaign data by type: {}, status: {}, campaignNumber: {} in tenant: {}",
                type, status, campaignNumber, tenantId);

        java.util.List<Map<String, Object>> allRecords = new java.util.ArrayList<>();
        int limit = 200;
        int offset = 0;

        try {
            while (true) {
                Map<String, Object> searchCriteria = new HashMap<>();
                searchCriteria.put("tenantId", tenantId);
                searchCriteria.put("type", type);

                if (status != null && !status.trim().isEmpty()) {
                    searchCriteria.put("status", status);
                }
                if (campaignNumber != null && !campaignNumber.trim().isEmpty()) {
                    searchCriteria.put("campaignNumber", campaignNumber);
                }

                Map<String, Object> pagination = new HashMap<>();
                pagination.put("limit", limit);
                pagination.put("offset", offset);

                Map<String, Object> payload = Map.of(
                    "RequestInfo", sanitizedRequestInfo,
                    "SearchCriteria", searchCriteria,
                        "Pagination", pagination
                );

                String url = config.getCampaignDataSearchUrl();

                @SuppressWarnings("unchecked")
                Map<String, Object> response = serviceRequestRepository.fetchResult(
                        new StringBuilder(url), payload, Map.class);

                if (response == null || response.get("CampaignData") == null) {
                    break;
                }

                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> batch =
                    (java.util.List<Map<String, Object>>) response.get("CampaignData");

                if (batch.isEmpty()) {
                    break;
                }

                allRecords.addAll(batch);
                log.debug("Fetched batch of {} records at offset {}", batch.size(), offset);

                if (batch.size() < limit) {
                    break;
                }
                offset += limit;
            }

            log.info("Total {} records fetched for type: {} in campaign: {}", allRecords.size(), type, campaignNumber);
            return allRecords;

        } catch (org.egov.tracer.model.CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error searching campaign data by type {}: {}", type, e.getMessage(), e);
            exceptionHandler.throwCustomException(
                    ErrorConstants.CAMPAIGN_DATA_SEARCH_ERROR,
                    ErrorConstants.CAMPAIGN_DATA_SEARCH_ERROR_MESSAGE + ": " + e.getMessage(), e);
            return null; // never reached
        }
    }

    /**
     * Build campaign search URL from configuration
     */
    /**
     * Which campaign's stored data pre-fills a generated sheet, and whether that is a clone reading its
     * source. Callers on the clone path use the flag to fail closed: a pre-fill error there must fail the
     * generation, not emit an empty, unstamped sheet that the immutable-join check would then trust.
     */
    public static final class DataSource {
        private final String campaignNumber;
        private final boolean fromCloneSource;

        public DataSource(String campaignNumber, boolean fromCloneSource) {
            this.campaignNumber = campaignNumber;
            this.fromCloneSource = fromCloneSource;
        }

        public String getCampaignNumber() {
            return campaignNumber;
        }

        public boolean isFromCloneSource() {
            return fromCloneSource;
        }
    }

    /**
     * Resolves which campaign's stored data should pre-fill a generated sheet.
     *
     * <p>Normally that is the campaign itself. A clone is the exception: until it has been uploaded it
     * owns no data of its own, because everything the operator expects to see still belongs to the
     * campaign it was cloned from. Without this, a clone's template comes out empty and the operator
     * either re-types everything or works on the parent's uploaded file — and rows on that file were
     * never written by the server, so they carry no row id and the immutable-join check has nothing to
     * compare them against. Pre-filling them here is what makes them protected.
     *
     * <p>The fallback is deliberately conditional on the clone owning <em>nothing</em> of this type, in
     * ANY status: campaign data rows are written when the clone's own upload is processed, so a single
     * row means the clone has uploaded and its own rows are authoritative — a row the operator deleted on
     * purpose stays deleted instead of being restored from the source on the next generation. Resolution
     * is one hop: a clone of a never-uploaded clone reads that clone's (empty) data, not the grandparent.
     *
     * @return the data source, or null if the reference cannot be resolved (the generators treat null as
     *         "nothing to pre-fill", exactly like the per-generator lookups this replaces)
     */
    public DataSource resolveDataSource(String referenceId, String type, String tenantId, RequestInfo requestInfo) {
        // Parity with the per-generator lookups this replaces: they caught every failure and returned null,
        // which the generators treat as "no campaign data to merge". searchCampaignById throws on a miss or
        // an HTTP failure, so without this guard the boundary sheet generation would fail outright.
        CampaignSearchResponse.CampaignDetail campaign;
        try {
            campaign = searchCampaignById(referenceId, tenantId, requestInfo);
        } catch (Exception e) {
            log.error("Error fetching campaign for reference ID {}: {}", referenceId, e.getMessage());
            return null;
        }
        if (campaign == null) {
            log.warn("No campaign found for reference ID: {}", referenceId);
            return null;
        }
        String ownNumber = campaign.getCampaignNumber();
        String clonedCampaignId = campaign.getAdditionalDetails() == null
                ? null
                : campaign.getAdditionalDetails().getClonedCampaignId();
        boolean isClone = clonedCampaignId != null && !clonedCampaignId.trim().isEmpty()
                && !clonedCampaignId.trim().equals(referenceId);

        if (ownNumber == null || ownNumber.isEmpty()) {
            if (isClone) {
                log.warn("Clone {} has no campaign number yet; its sheet will be generated without the source data", referenceId);
            }
            return new DataSource(ownNumber, false);
        }
        if (!isClone) {
            return new DataSource(ownNumber, false);
        }

        try {
            java.util.List<Map<String, Object>> own =
                    searchCampaignDataByType(type, null, ownNumber, tenantId, requestInfo);
            if (own != null && !own.isEmpty()) {
                return new DataSource(ownNumber, false);
            }
        } catch (Exception e) {
            // Reading the clone source after an unreadable own-data check could restore rows the operator
            // cleared, which is worse than generating the sheet we generate today.
            log.error("Could not determine whether campaign {} owns {} data; using its own data: {}",
                    ownNumber, type, e.getMessage(), e);
            return new DataSource(ownNumber, false);
        }

        // searchCampaignById throws rather than returning null when the campaign is missing. A clone whose
        // source has since been removed must still get a sheet, so any failure here falls back to the
        // clone's own (empty) data instead of failing the whole generation.
        CampaignSearchResponse.CampaignDetail source;
        try {
            source = searchCampaignById(clonedCampaignId.trim(), tenantId, requestInfo);
        } catch (Exception e) {
            log.warn("Clone source {} of campaign {} could not be resolved ({}); generating from its own data",
                    clonedCampaignId, ownNumber, e.getMessage());
            return new DataSource(ownNumber, false);
        }
        if (source == null || source.getCampaignNumber() == null || source.getCampaignNumber().isEmpty()) {
            log.warn("Clone source {} of campaign {} has no campaign number; generating from its own data",
                    clonedCampaignId, ownNumber);
            return new DataSource(ownNumber, false);
        }

        log.info("Campaign {} owns no {} data; pre-filling from clone source {}",
                ownNumber, type, source.getCampaignNumber());
        return new DataSource(source.getCampaignNumber(), true);
    }

    /** Convenience for callers that only need the number (see {@link #resolveDataSource}). */
    public String resolveDataSourceCampaignNumber(String referenceId, String type, String tenantId,
                                                  RequestInfo requestInfo) {
        DataSource dataSource = resolveDataSource(referenceId, type, tenantId, requestInfo);
        return dataSource == null ? null : dataSource.getCampaignNumber();
    }

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
                    .userInfo(User.builder().tenantId(tenantId).build())
                    .build();
        }
        User user = requestInfo.getUserInfo();
        if (user == null) {
            user = User.builder().tenantId(tenantId).build();
            requestInfo.setUserInfo(user);
            return requestInfo;
        }
        if (user.getTenantId() == null || user.getTenantId().isBlank()) {
            user.setTenantId(tenantId);
        }
        return requestInfo;
    }
}
