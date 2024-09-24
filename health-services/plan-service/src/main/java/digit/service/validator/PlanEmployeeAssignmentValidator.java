package digit.service.validator;

import digit.util.CampaignUtil;
import digit.util.CommonUtil;
import digit.util.HrmsUtil;
import digit.web.models.*;
import digit.web.models.hrms.EmployeeResponse;
import digit.web.models.projectFactory.Boundary;
import digit.web.models.projectFactory.CampaignDetail;
import digit.web.models.projectFactory.CampaignResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.*;

@Slf4j
@Component
public class PlanEmployeeAssignmentValidator {

    private MultiStateInstanceUtil centralInstanceUtil;

    private HrmsUtil hrmsUtil;

    private CommonUtil commonUtil;

    private CampaignUtil campaignUtil;

    public PlanEmployeeAssignmentValidator(MultiStateInstanceUtil centralInstanceUtil, HrmsUtil hrmsUtil, CommonUtil commonUtil, CampaignUtil campaignUtil) {
        this.centralInstanceUtil = centralInstanceUtil;
        this.hrmsUtil = hrmsUtil;
        this.commonUtil = commonUtil;
        this.campaignUtil = campaignUtil;
    }


    /**
     * This method validates the create request for plan employee assignment.
     *
     * @param request The create request for plan employee assignment
     */
    public void validateCreate(PlanEmployeeAssignmentRequest request) {
        PlanEmployeeAssignment planEmployeeAssignment = request.getPlanEmployeeAssignment();
        String rootTenantId = centralInstanceUtil.getStateLevelTenant(request.getPlanEmployeeAssignment().getTenantId());
        List<PlanConfiguration> planConfigurations = commonUtil.searchPlanConfigId(planEmployeeAssignment.getPlanConfigurationId(), rootTenantId);

        // Validate if plan config id exists
        validatePlanConfigId(planConfigurations);

        // Validate if employee exists against hrms
        validateEmployeeAgainstHRMS(request);

        // Validate campaign id and employee jurisdiction
        validateCampaignDetails(planConfigurations.get(0).getCampaignId(), rootTenantId, request);
    }


    /**
     * This method validates campaign id and employee's jurisdiction against project factory
     *
     * @param campaignId                    the campaign id corresponding to the plan config id provided in the request
     * @param tenantId                      the tenant id provided in the request
     * @param planEmployeeAssignmentRequest the plan employee assignment request provided
     */
    private void validateCampaignDetails(String campaignId, String tenantId, PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest) {

        PlanEmployeeAssignment planEmployeeAssignment = planEmployeeAssignmentRequest.getPlanEmployeeAssignment();
        CampaignResponse campaignResponse = campaignUtil.fetchCampaignData(planEmployeeAssignmentRequest.getRequestInfo(), campaignId, tenantId);

        // Validate if campaign id exists against project factory
        validateCampaignId(campaignResponse);

        // Validate the provided jurisdiction for employee
        validateEmployeeJurisdiction(campaignResponse.getCampaignDetails().get(0), planEmployeeAssignment);
    }

    /**
     * This method validates if employee's jurisdiction exist in campaign details
     *
     * @param campaignDetail         the campaign details for the corresponding campaign id
     * @param planEmployeeAssignment the plan employee assignment provided in request
     */
    private void validateEmployeeJurisdiction(CampaignDetail campaignDetail, PlanEmployeeAssignment planEmployeeAssignment) {

        // Collect all boundary code for the campaign
        Set<String> boundaryCode = campaignDetail.getBoundaries().stream()
                .map(Boundary::getCode)
                .collect(Collectors.toSet());

        planEmployeeAssignment.getJurisdiction().stream()
                .forEach(jurisdiction -> {
                    if (!boundaryCode.contains(jurisdiction)) {
                        throw new CustomException(INVALID_EMPLOYEE_JURISDICTION_CODE, INVALID_EMPLOYEE_JURISDICTION_MESSAGE);
                    }
                });

    }

    /**
     * This method validates if the campaign id provided in the request exists
     *
     * @param campaignResponse The campaign details response from project factory
     */
    private void validateCampaignId(CampaignResponse campaignResponse) {

        if (CollectionUtils.isEmpty(campaignResponse.getCampaignDetails())) {
            throw new CustomException(NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_CODE, NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_MESSAGE);
        }
    }

    /**
     * This method validates if the employee provided in plan employee assignment request exist in hrms
     *
     * @param request The request for plan employee assignment
     */
    private void validateEmployeeAgainstHRMS(PlanEmployeeAssignmentRequest request) {

        PlanEmployeeAssignment planEmployeeAssignment = request.getPlanEmployeeAssignment();
        EmployeeResponse employeeResponse = hrmsUtil.fetchHrmsData(request.getRequestInfo(), planEmployeeAssignment.getEmployeeId(), planEmployeeAssignment.getTenantId());

        if (CollectionUtils.isEmpty(employeeResponse.getEmployees())) {
            log.error(NO_HRMS_DATA_FOUND_FOR_GIVEN_EMPLOYEE_ID_MESSAGE + " - " + planEmployeeAssignment.getEmployeeId());
            throw new CustomException(NO_HRMS_DATA_FOUND_FOR_GIVEN_EMPLOYEE_ID_CODE, NO_HRMS_DATA_FOUND_FOR_GIVEN_EMPLOYEE_ID_MESSAGE);
        }
    }


    /**
     * This method validates if the plan configuration id provided in the request exists
     *
     * @param planConfigurations The list of plan configuration for the provided plan config id
     */
    private void validatePlanConfigId(List<PlanConfiguration> planConfigurations) {
        if (CollectionUtils.isEmpty(planConfigurations)) {
            throw new CustomException(INVALID_PLAN_CONFIG_ID_CODE, INVALID_PLAN_CONFIG_ID_MESSAGE);
        }
    }

    /**
     * Validates the search request for plan employee assignment
     *
     * @param request the request to search plan employee assignment
     */
    public void validateSearch(PlanEmployeeAssignmentSearchRequest request) {
        PlanEmployeeAssignmentSearchCriteria searchCriteria = request.getPlanEmployeeAssignmentSearchCriteria();
        if (Objects.isNull(searchCriteria)) {
            throw new CustomException(SEARCH_CRITERIA_EMPTY_CODE, SEARCH_CRITERIA_EMPTY_MESSAGE);
        }

        if (StringUtils.isEmpty(searchCriteria.getTenantId())) {
            throw new CustomException(TENANT_ID_EMPTY_CODE, TENANT_ID_EMPTY_MESSAGE);
        }
    }

    /**
     * This method validates the update request for plan employee assignment.
     *
     * @param request The update request for plan employee assignment.
     */
    public void validateUpdate(PlanEmployeeAssignmentRequest request) {
        PlanEmployeeAssignment planEmployeeAssignment = request.getPlanEmployeeAssignment();
        String rootTenantId = centralInstanceUtil.getStateLevelTenant(request.getPlanEmployeeAssignment().getTenantId());
        List<PlanConfiguration> planConfigurations = commonUtil.searchPlanConfigId(planEmployeeAssignment.getPlanConfigurationId(), rootTenantId);

        // Validate if Plan employee assignment exists
        validatePlanEmployeeAssignment(planEmployeeAssignment);

        // Validate if plan config id exists
        validatePlanConfigId(planConfigurations);

        // Validate if employee exists against hrms
        validateEmployeeAgainstHRMS(request);

        // Validate campaign id and employee jurisdiction
        validateCampaignDetails(planConfigurations.get(0).getCampaignId(), rootTenantId, request);

    }

    /**
     * This method validates if the plan employee assignment id provided in the update request exists
     *
     * @param planEmployeeAssignment The plan employee assignment details from the request
     */
    private void validatePlanEmployeeAssignment(PlanEmployeeAssignment planEmployeeAssignment) {

    }
}
