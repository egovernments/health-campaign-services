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
        StringBuilder uri = new StringBuilder();
        uri = uri.append(configs.getProjectFactoryHost()).append(configs.getProjectFactorySearchEndPoint());

        CampaignSearchReq campaignSearchReq = getSearchReq(requestInfo, campaignId, tenantId);
        CampaignResponse campaignResponse = null;
        try {
            campaignResponse = restTemplate.postForObject(uri.toString(), campaignSearchReq, CampaignResponse.class);
        } catch (Exception e) {
            throw new CustomException(NO_CAMPAIGN_RESPONSE_FOUND_FOR_GIVEN_CAMPAIGN_ID_CODE,NO_CAMPAIGN_RESPONSE_FOUND_FOR_GIVEN_CAMPAIGN_ID_MESSAGE);
        }

        if (CollectionUtils.isEmpty(campaignResponse.getCampaignDetails())) {
            throw new CustomException(NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_CODE, NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_MESSAGE);
        }

        return campaignResponse;
    }

    private CampaignSearchReq getSearchReq(RequestInfo requestInfo, String campaignId, String tenantId) {
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