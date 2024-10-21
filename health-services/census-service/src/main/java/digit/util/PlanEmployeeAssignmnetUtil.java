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
     * @param planEmployeeAssignmentSearchRequest request containint the planEmployeeAssignment search criteria
     * @return returns planEmployeeAssignment for provided search criteria.
     */
    public PlanEmployeeAssignmentResponse fetchPlanEmployeeAssignment(PlanEmployeeAssignmentSearchRequest planEmployeeAssignmentSearchRequest) {

        // Get plan employee assignment uri
        StringBuilder uri = getPlanEmployeeAssignmentUri();

        PlanEmployeeAssignmentResponse response = new PlanEmployeeAssignmentResponse();

        try {
            response = restTemplate.postForObject(uri.toString(), planEmployeeAssignmentSearchRequest, PlanEmployeeAssignmentResponse.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_EMPLOYEE_ASSIGNMENT_DETAILS, e);
        }

        return response;
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
