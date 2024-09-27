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
     * @param planFacilityRequest The PlanFacilityRequest object contains the plan facility to be enriched.
     */
    public void enrichPlanFacilityUpdate(PlanFacilityRequest planFacilityRequest) {
        if (planFacilityRequest == null || planFacilityRequest.getPlanFacility() == null) {
            throw new IllegalArgumentException("PlanFacilityRequest or PlanFacility cannot be null");
        }
        PlanFacility planFacility = planFacilityRequest.getPlanFacility();
        //enrich audit details
        planFacility.setAuditDetails(prepareAuditDetails(planFacilityRequest.getPlanFacility().getAuditDetails(), planFacilityRequest.getRequestInfo(), Boolean.FALSE));
    }
}
