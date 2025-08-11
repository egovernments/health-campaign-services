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
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
@Slf4j
public class ProjectFactoryService {
    private final TransformerProperties transformerProperties;

    private final ServiceRequestClient serviceRequestClient;

    private final ObjectMapper objectMapper;


    private final TransformerCacheService cacheService;

    public ProjectFactoryService(TransformerProperties transformerProperties, ServiceRequestClient serviceRequestClient, ObjectMapper objectMapper, TransformerCacheService cacheService) {
        this.transformerProperties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
    }

    public String getCampaignIdFromCampaignNumber(String tenantId, Boolean isActive, String campaignNumber) {
        String campaignId = null;

        campaignId = cacheService.get(campaignNumber, String.class);
        if (campaignId != null) {
            log.debug("Picking campaign id {} from cache for campaign number {}", campaignId, campaignNumber);
            return campaignId;
        }

        log.debug("Cache missed for campaign number {}", campaignNumber);
        List<CampaignDetails> campaignDetailsList = getCampaign(tenantId, isActive, campaignNumber);
        if (!CollectionUtils.isEmpty(campaignDetailsList)) {
            //The API is expected to return only one campaign for the params used
            campaignId = campaignDetailsList.get(0).getId();
            cacheService.put(campaignNumber, campaignId);
        }
        return campaignId;
    }

    private List<CampaignDetails> getCampaign(String tenantId, Boolean isActive, String campaignNumber) {
        String msgId = System.currentTimeMillis() + "|" + transformerProperties.getLocalizationLocaleCode();
        CampaignSearchRequest request = CampaignSearchRequest.builder()
                .requestInfo(RequestInfo.builder()
                        .msgId(msgId)
                        .userInfo(User.builder()
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
        StringBuilder uri = new StringBuilder()
                .append(transformerProperties.getProjectFactoryHost())
                .append(transformerProperties.getProjectFactorySearchUrl());
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
