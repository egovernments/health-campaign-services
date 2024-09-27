package digit.util;

import digit.config.Configuration;
import digit.web.models.projectFactory.*;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static digit.config.ServiceConstants.*;

@Slf4j
@Component
public class CampaignUtil {

    private RestTemplate restTemplate;

    private Configuration configs;

    public CampaignUtil(RestTemplate restTemplate, Configuration configs) {
        this.restTemplate = restTemplate;
        this.configs = configs;
    }

    public CampaignResponse fetchCampaignData(RequestInfo requestInfo, String campaignId, String tenantId) {
        StringBuilder uri = new StringBuilder();
        uri = uri.append(configs.getProjectFactoryHost()).append(configs.getProjectFactorySearchEndPoint());

        CampaignSearchReq campaignSearchReq = getSearchReq(requestInfo, campaignId, tenantId);
        CampaignResponse campaignResponse = new CampaignResponse();
        try {
            campaignResponse = restTemplate.postForObject(uri.toString(), campaignSearchReq, CampaignResponse.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_PROJECT_FACTORY, e);
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