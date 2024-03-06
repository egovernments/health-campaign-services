package digit.service;

import digit.web.models.PlanCreateRequest;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.springframework.stereotype.Component;

@Component
public class PlanEnricher {


    public void enrichPlanCreate(PlanCreateRequest body) {
        UUIDEnrichmentUtil.enrichRandomUuid(body.getPlan(), "id");
    }
}
