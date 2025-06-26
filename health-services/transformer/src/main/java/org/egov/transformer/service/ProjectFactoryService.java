package org.egov.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.models.projectFactory.CampaignDetails;
import org.egov.transformer.models.projectFactory.CampaignSearchCriteria;
import org.egov.transformer.models.projectFactory.CampaignSearchRequest;
import org.egov.transformer.models.projectFactory.CampaignSearchResponse;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ProjectFactoryService {
    private final TransformerProperties transformerProperties;

    private final ServiceRequestClient serviceRequestClient;

    private final ObjectMapper objectMapper;

    private final CacheManager cacheManager;

    public ProjectFactoryService(TransformerProperties transformerProperties, ServiceRequestClient serviceRequestClient, ObjectMapper objectMapper, CacheManager cacheManager) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManager;
    }

    public String getCampaignIdFromCampaignNumber(String tenantId, Boolean isActive, String campaignNumber) {
        String campaignId = null;
        Cache cache = cacheManager.getCache("campaignCache");

        if (cache != null) {
            campaignId = cache.get(campaignNumber, String.class);
            if (campaignId != null) {
                log.debug("Picking campaign id {} from cache for campaign number {}", campaignId, campaignNumber);
                return campaignId;
            }
        }

        log.debug("Cache missed for campaign number {}", campaignNumber);
        List<CampaignDetails> campaignDetailsList = getCampaign(tenantId, isActive, campaignNumber);
        if (!CollectionUtils.isEmpty(campaignDetailsList)) {
            //The API is expected to return only one campaign for the params used
            campaignId = campaignDetailsList.get(0).getId();
            if (cache != null) {
                cache.put(campaignNumber, campaignId);
            }
        }
        return campaignId;
    }

    private List<CampaignDetails> getCampaign(String tenantId, Boolean isActive, String campaignNumber) {
        CampaignSearchRequest request = CampaignSearchRequest.builder()
                .requestInfo(RequestInfo.builder().
                        userInfo(User.builder()
                                .tenantId(tenantId)
                                .build())
                        .build())
                .campaignDetails(CampaignSearchCriteria.builder()
                        .campaignNumber(campaignNumber)
                        .tenantId(tenantId)
                        .isActive(isActive)
                        .build())
                .build();
        CampaignSearchResponse response;
        StringBuilder uri = new StringBuilder(transformerProperties.getProjectFactoryHost()
                + transformerProperties.getProjectFactorySearchUrl());
        log.info("URI: {}, \n, requestBody: {}", uri, request);
        try {
            log.info("Fetching campaign details for tenantId: {}, campaignNumber: {}", tenantId, campaignNumber);
            response = serviceRequestClient.fetchResult(
                    uri,
                    request,
                    CampaignSearchResponse.class
            );
        } catch (Exception e) {
            log.error("Error while fetching campaign list {}", ExceptionUtils.getStackTrace(e));
            log.debug("Returning null for campaign number {}", campaignNumber);
            return null;
        }
        return response.getCampaignDetails();
    }


}
