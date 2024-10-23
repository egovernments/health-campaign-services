package digit.util;

import digit.config.Configuration;
import digit.web.models.plan.PlanEmployeeAssignmentResponse;
import digit.web.models.plan.PlanEmployeeAssignmentSearchCriteria;
import digit.web.models.plan.PlanEmployeeAssignmentSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static digit.config.ServiceConstants.ERROR_WHILE_FETCHING_EMPLOYEE_ASSIGNMENT_DETAILS;

@Slf4j
@Component
public class PlanEmployeeAssignmnetUtil {

    private RestTemplate restTemplate;

    private Configuration configs;

    public PlanEmployeeAssignmnetUtil(RestTemplate restTemplate, Configuration configs) {
        this.restTemplate = restTemplate;
        this.configs = configs;
    }

    /**
     * This method fetches plan employee assignment from plan service for provided employeeID.
     *
     * @param requestInfo  request info from the request.
     * @param employeeId   employee id from the request.
     * @param planConfigId plan configuration id from the request.
     * @param tenantId     tenant id from the request.
     * @param roles        allowed roles for census
     * @param jurisdiction list of ancestral boundary codes for the given boundary
     * @return returns planEmployeeAssignment for provided search criteria.
     */
    public PlanEmployeeAssignmentResponse fetchPlanEmployeeAssignment(RequestInfo requestInfo, String employeeId, String planConfigId, String tenantId, List<String> roles, List<String> jurisdiction) {
        // Get plan employee assignment uri
        StringBuilder uri = getPlanEmployeeAssignmentUri();

        // Get search request body for plan employee assignment
        PlanEmployeeAssignmentSearchRequest searchRequest = getPlanEmployeeAssignmentRequest(requestInfo, employeeId, planConfigId, tenantId, roles, jurisdiction);
        PlanEmployeeAssignmentResponse response = new PlanEmployeeAssignmentResponse();

        try {
            response = restTemplate.postForObject(uri.toString(), searchRequest, PlanEmployeeAssignmentResponse.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_EMPLOYEE_ASSIGNMENT_DETAILS, e);
        }

        return response;
    }

    /**
     * This method builds the search request body for plan employee assignment search
     *
     * @param requestInfo  request info from the request.
     * @param employeeId   employee id from the request.
     * @param planConfigId plan configuration id from the request.
     * @param tenantId     tenant id from the request.
     * @param roles        allowed roles for census
     * @param jurisdiction list of ancestral boundary codes for the given boundary
     * @return the search request for pln employee assignment search
     */
    private PlanEmployeeAssignmentSearchRequest getPlanEmployeeAssignmentRequest(RequestInfo requestInfo, String employeeId, String planConfigId, String tenantId, List<String> roles, List<String> jurisdiction) {
        PlanEmployeeAssignmentSearchCriteria searchCriteria = PlanEmployeeAssignmentSearchCriteria.builder()
                .tenantId(tenantId)
                .planConfigurationId(planConfigId)
                .employeeId(employeeId)
                .role(roles)
                .jurisdiction(jurisdiction)
                .build();

        return PlanEmployeeAssignmentSearchRequest.builder()
                .requestInfo(requestInfo)
                .planEmployeeAssignmentSearchCriteria(searchCriteria)
                .build();
    }

    /**
     * This method creates the uri for plan employee assignment search
     *
     * @return uri for plan employee assignment search
     */
    private StringBuilder getPlanEmployeeAssignmentUri() {
        StringBuilder uri = new StringBuilder();
        return uri.append(configs.getPlanServiceHost()).append(configs.getPlanEmployeeAssignmentSearchEndpoint());
    }
}
