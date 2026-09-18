package digit.service.enrichment;

import digit.util.CommonUtil;
import digit.web.models.*;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.springframework.stereotype.Component;

import static digit.config.ServiceConstants.*;
import static org.egov.common.utils.AuditDetailsEnrichmentUtil.prepareAuditDetails;

@Component
public class PlanEmployeeAssignmentEnricher {

    private CommonUtil commonUtil;

    public PlanEmployeeAssignmentEnricher(CommonUtil commonUtil) {
        this.commonUtil = commonUtil;
    }

    /**
     * Enriches the PlanEmployeeAssignmentRequest with id and audit details and sets active as true.
     *
     * @param request The PlanEmployeeAssignmentRequest body to be enriched
     */
    public void enrichCreate(PlanEmployeeAssignmentRequest request) {
        PlanEmployeeAssignment planEmployeeAssignment = request.getPlanEmployeeAssignment();

        // Generate id for Plan employee assignment body
        UUIDEnrichmentUtil.enrichRandomUuid(planEmployeeAssignment, ID);

        // Set active true
        planEmployeeAssignment.setActive(Boolean.TRUE);

        // Set Audit Details for Plan employee assignment
        planEmployeeAssignment.setAuditDetails(prepareAuditDetails(planEmployeeAssignment.getAuditDetails(),
                request.getRequestInfo(),
                Boolean.TRUE));

        // Add plan config name to which the employee is mapped
        planEmployeeAssignment.setPlanConfigurationName(commonUtil.getPlanConfigName(planEmployeeAssignment.getTenantId(), planEmployeeAssignment.getPlanConfigurationId()));
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
