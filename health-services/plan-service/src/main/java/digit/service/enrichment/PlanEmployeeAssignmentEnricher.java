package digit.service.enrichment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import digit.repository.PlanEmployeeAssignmentRepository;
import digit.util.CommonUtil;
import digit.web.models.*;
import org.egov.common.utils.UUIDEnrichmentUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static digit.config.ServiceConstants.*;
import static org.egov.common.utils.AuditDetailsEnrichmentUtil.prepareAuditDetails;

@Component
public class PlanEmployeeAssignmentEnricher {

    private ObjectMapper objectMapper;

    private PlanEmployeeAssignmentRepository repository;

    private CommonUtil commonUtil;

    public PlanEmployeeAssignmentEnricher(ObjectMapper objectMapper, CommonUtil commonUtil, PlanEmployeeAssignmentRepository repository) {
        this.objectMapper = objectMapper;
        this.commonUtil = commonUtil;
        this.repository = repository;
    }

    /**
     * Enriches the PlanEmployeeAssignmentRequest with id and audit details and sets active as true.
     *
     * @param request The PlanEmployeeAssignmentRequest body to be enriched
     */
    public void enrichCreate(PlanEmployeeAssignmentRequest request) {
        PlanEmployeeAssignment planEmployeeAssignment = request.getPlanEmployeeAssignment();

        // Generate id for Plan employee assignment body
        UUIDEnrichmentUtil.enrichRandomUuid(planEmployeeAssignment, "id");

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
