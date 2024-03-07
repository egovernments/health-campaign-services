package digit.service;

import digit.web.models.PlanRequest;
import org.egov.common.utils.AuditDetailsEnrichmentUtil;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class PlanEnricher {


    public void enrichPlanCreate(PlanRequest body) {
        // Generate id for plan
        UUIDEnrichmentUtil.enrichRandomUuid(body.getPlan(), "id");

        // Generate id for activities
        body.getPlan().getActivities().forEach(activity -> UUIDEnrichmentUtil.enrichRandomUuid(activity, "id"));

        // Generate id for activity conditions
        body.getPlan().getActivities().forEach(activity -> {
            if(!CollectionUtils.isEmpty(activity.getConditions())) {
                UUIDEnrichmentUtil.enrichRandomUuid(activity.getConditions(), "id");
            }
        });

        // Generate id for resources
        body.getPlan().getResources().forEach(resource -> UUIDEnrichmentUtil.enrichRandomUuid(resource, "id"));

        // Generate id for targets
        body.getPlan().getTargets().forEach(target -> UUIDEnrichmentUtil.enrichRandomUuid(target, "id"));

        // Enrich audit details
        body.getPlan().setAuditDetails(AuditDetailsEnrichmentUtil
                .prepareAuditDetails(body.getPlan().getAuditDetails(), body.getRequestInfo(), Boolean.TRUE));

    }
}
