package digit.util;

import digit.config.Configuration;
import digit.web.models.Pagination;
import digit.web.models.projectFactory.*;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import static digit.config.ServiceConstants.*;
import java.util.Collections;

@Slf4j
@Component
public class CampaignUtil {

    private RestTemplate restTemplate;

    private Configuration configs;

    public CampaignUtil(RestTemplate restTemplate, Configuration configs) {
        this.restTemplate = restTemplate;
        this.configs = configs;
    }

    /**
     * This method calls external service to validate campaign id and service boundaries against project factory
     *
     * @param requestInfo
     * @param campaignId
     * @param tenantId
     */
    public CampaignResponse fetchCampaignData(RequestInfo requestInfo, String campaignId, String tenantId) {
        // Build the URI for calling the Project Factory service
        String uri = buildCampaignSearchUri();

        // Prepare the search request object with required campaign ID, tenant ID, and request information
        CampaignSearchReq campaignSearchReq = getCampaignSearchRequest(requestInfo, campaignId, tenantId);
        CampaignResponse campaignResponse = null;
        try {
            campaignResponse = restTemplate.postForObject(uri.toString(), campaignSearchReq, CampaignResponse.class);
        } catch (Exception e) {
            throw new CustomException(NO_CAMPAIGN_RESPONSE_FOUND_FOR_GIVEN_CAMPAIGN_ID_CODE,NO_CAMPAIGN_RESPONSE_FOUND_FOR_GIVEN_CAMPAIGN_ID_MESSAGE);
        }

        // Validate that the response contains campaign details, otherwise throw an exception
        if (CollectionUtils.isEmpty(campaignResponse.getCampaignDetails())) {
            throw new CustomException(NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_CODE, NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_MESSAGE);
        }

        return campaignResponse;
    }

    /**
     * Constructs the URI for the external service to fetch campaign data.
     *
     * @return The complete URI as a String.
     */
    private String buildCampaignSearchUri() {
        return new StringBuilder()
                .append(configs.getProjectFactoryHost())
                .append(configs.getProjectFactorySearchEndPoint())
                .toString();
    }

    /**
     * Creates the request object for fetching campaign data.
     *
     * @param requestInfo Information about the request such as user details and correlation ID.
     * @param campaignId The ID of the campaign to be searched.
     * @param tenantId The tenant identifier (for multi-tenant support).
     * @return CampaignSearchReq The request object containing the search criteria and request info.
     */
    private CampaignSearchReq getCampaignSearchRequest(RequestInfo requestInfo, String campaignId, String tenantId) {
        Pagination pagination = Pagination.builder().limit(configs.getDefaultLimit()).offset(configs.getDefaultOffset()).build();
        CampaignSearchCriteria searchCriteria = CampaignSearchCriteria.builder()
                .ids(Collections.singletonList(campaignId))
                .tenantId(tenantId)
                .pagination(pagination)
                .build();

        return CampaignSearchReq.builder()
                .requestInfo(requestInfo)
                .campaignSearchCriteria(searchCriteria)
                .build();
    }
}