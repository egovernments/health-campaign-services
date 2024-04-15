package org.egov.processor.service;


import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.processor.util.PlanConfigurationUtil;
import org.egov.processor.web.models.PlanConfiguration;
import org.egov.processor.web.models.PlanConfigurationSearchCriteria;
import org.egov.processor.web.models.PlanConfigurationSearchRequest;
import org.egov.processor.web.models.PlanRequest;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ResourceEstimationService {

    private PlanConfigurationUtil planConfigurationUtil;

    private ExcelParser parser;

    public ResourceEstimationService(PlanConfigurationUtil planConfigurationUtil, ExcelParser parser) {
        this.planConfigurationUtil = planConfigurationUtil;
        this.parser = parser;
    }

    public void estimateResources(PlanRequest planRequest)
    {
        log.info("Plan Configuration ID - " + planRequest.getPlan().getPlanConfigurationId());
        PlanConfigurationSearchCriteria planConfigurationSearchCriteria = PlanConfigurationSearchCriteria.builder()
                .tenantId(planRequest.getPlan().getTenantId()).id(planRequest.getPlan().getPlanConfigurationId()).build();
        PlanConfigurationSearchRequest planConfigurationSearchRequest = PlanConfigurationSearchRequest.builder().planConfigurationSearchCriteria(planConfigurationSearchCriteria).requestInfo(new RequestInfo()).build();
        List<PlanConfiguration> planConfigurationls = planConfigurationUtil.search(planConfigurationSearchRequest);

        parser.parseFileData(planRequest.getPlan(), planConfigurationls.get(0));
    }
}
