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

        // Add a list of plan config names to which the employee is mapped
        enrichWithPlanConfigName(planEmployeeAssignment);
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

    /**
     * This method enriches the additional details of plan employee assignment object with the name of all the plan configs to which the employee is mapped to.
     *
     * @param planEmployeeAssignment the object whose additional details is to be enriched
     */
    private void enrichWithPlanConfigName(PlanEmployeeAssignment planEmployeeAssignment) {

        String tenantId = planEmployeeAssignment.getTenantId();
        List<PlanEmployeeAssignment> employeeAssignmentListFromSearch = getAllEmployeeAssignment(planEmployeeAssignment.getEmployeeId(), tenantId);
        try {

            // Get or create the additionalDetails as an ObjectNode
            ObjectNode objectNode = objectMapper.convertValue(planEmployeeAssignment.getAdditionalDetails(), ObjectNode.class);

            // Create an ArrayNode to hold the list of plan configuration names
            ArrayNode planConfigNamesNode = objectNode.putArray(PLAN_CONFIG_NAME_FIELD);

            // Populate the ArrayNode with the plan configuration names
            employeeAssignmentListFromSearch.stream()
                    .map(PlanEmployeeAssignment::getPlanConfigurationId)
                    .map(id -> getPlanConfigNameById(id, tenantId))
                    .forEach(planConfigNamesNode::add);

            // Set the updated additionalDetails back into the planEmployeeAssignment
            planEmployeeAssignment.setAdditionalDetails(objectMapper.convertValue(objectNode, Map.class));

        } catch (Exception e) {
            throw new CustomException(ERROR_WHILE_ENRICHING_ADDITIONAL_DETAILS_CODE, ERROR_WHILE_ENRICHING_ADDITIONAL_DETAILS_MESSAGE);
        }
    }

    private String getPlanConfigNameById(String planConfigId, String tenantId) {
        List<PlanConfiguration> planConfigurations = commonUtil.searchPlanConfigId(planConfigId, tenantId);
        return planConfigurations.get(0).getName();
    }

    private List<PlanEmployeeAssignment> getAllEmployeeAssignment(String employeeId, String tenantId) {
        PlanEmployeeAssignmentSearchCriteria searchCriteria = PlanEmployeeAssignmentSearchCriteria.builder()
                .tenantId(tenantId)
                .employeeId(Collections.singletonList(employeeId))
                .build();

        return repository.search(searchCriteria);
    }
}
