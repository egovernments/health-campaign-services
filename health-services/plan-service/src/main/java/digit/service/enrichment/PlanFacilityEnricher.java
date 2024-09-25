package digit.service.enrichment;

import digit.web.models.PlanFacility;
import digit.web.models.PlanFacilityRequest;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.AuditDetailsEnrichmentUtil.prepareAuditDetails;

@Component
public class PlanFacilityEnricher {

    /**
     * Enriches the plan facility update request
     *
     * @param body
     */
    public void enrichPlanFacilityUpdate(PlanFacilityRequest body) {
        PlanFacility planFacility = body.getPlanFacility();
        //enrich audit details
        planFacility.setAuditDetails(prepareAuditDetails(body.getPlanFacility().getAuditDetails(), body.getRequestInfo(), Boolean.FALSE));
    }
}
