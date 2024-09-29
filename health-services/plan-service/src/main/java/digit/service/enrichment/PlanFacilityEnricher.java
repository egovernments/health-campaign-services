package digit.service.enrichment;

import digit.web.models.PlanFacilityRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.AuditDetailsEnrichmentUtil;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PlanFacilityEnricher {

    /**
     * Enriches the plan facility create request
     *
     * @param planFacilityRequest
     */
    public void enrichPlanFacilityCreate(@Valid PlanFacilityRequest planFacilityRequest) {
        if (planFacilityRequest.getPlanFacility() == null) {
            throw new IllegalArgumentException("Plan Facility details are missing in the request.");
        }

        // Generate id for plan facility
        UUIDEnrichmentUtil.enrichRandomUuid(planFacilityRequest.getPlanFacility(), "id");

        // Enrich audit details
        planFacilityRequest.getPlanFacility().setAuditDetails(AuditDetailsEnrichmentUtil
                .prepareAuditDetails(planFacilityRequest.getPlanFacility().getAuditDetails(), planFacilityRequest.getRequestInfo(), Boolean.TRUE));

        //Set Active
        planFacilityRequest.getPlanFacility().setActive(true);

    }
}
