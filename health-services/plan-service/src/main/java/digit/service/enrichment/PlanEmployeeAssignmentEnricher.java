package digit.service.enrichment;

import digit.web.models.PlanEmployeeAssignment;
import digit.web.models.PlanEmployeeAssignmentRequest;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.AuditDetailsEnrichmentUtil.prepareAuditDetails;

@Component
public class PlanEmployeeAssignmentEnricher {

    /**
     * Enriches the PlanEmployeeAssignmentRequest with id and audit details.
     *
     * @param request The PlanEmployeeAssignmentRequest body to be enriched
     */
    public void enrichCreate(PlanEmployeeAssignmentRequest request) {
        PlanEmployeeAssignment planEmployeeAssignment = request.getPlanEmployeeAssignment();

        // Generate id for Plan employee assignment body
        UUIDEnrichmentUtil.enrichRandomUuid(planEmployeeAssignment, "id");

        // Set Audit Details for Plan employee assignment
        planEmployeeAssignment.setAuditDetails(prepareAuditDetails(planEmployeeAssignment.getAuditDetails(),
                request.getRequestInfo(),
                Boolean.TRUE));
    }

    /**
     * Enriches the PlanEmployeeAssignmentRequest for updating an existing plan employee assignment with audit details.
     *
     * @param request The PlanEmployeeAssignmentRequest body to be enriched
     */
    public void enrichUpdate(PlanEmployeeAssignmentRequest request) {
        PlanEmployeeAssignment planEmployeeAssignment = request.getPlanEmployeeAssignment();

        // Set Audit Details for Plan employee assignment update
        planEmployeeAssignment.setAuditDetails(prepareAuditDetails(planEmployeeAssignment.getAuditDetails(),
                request.getRequestInfo(),
                Boolean.FALSE));
    }
}
