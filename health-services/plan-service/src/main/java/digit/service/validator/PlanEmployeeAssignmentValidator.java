package digit.service.validator;

import com.jayway.jsonpath.JsonPath;
import digit.config.Configuration;
import digit.repository.PlanEmployeeAssignmentRepository;
import digit.util.CampaignUtil;
import digit.util.CommonUtil;
import digit.util.HrmsUtil;
import digit.util.MdmsUtil;
import digit.web.models.*;
import digit.web.models.hrms.EmployeeResponse;
import digit.web.models.projectFactory.Boundary;
import digit.web.models.projectFactory.CampaignDetail;
import digit.web.models.projectFactory.CampaignResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.Role;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.*;

import java.util.*;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.*;

@Slf4j
@Component
public class PlanEmployeeAssignmentValidator {

    private MultiStateInstanceUtil centralInstanceUtil;

    private MdmsUtil mdmsUtil;

    private HrmsUtil hrmsUtil;

    private CommonUtil commonUtil;

    private CampaignUtil campaignUtil;

    private PlanEmployeeAssignmentRepository repository;

    private Configuration config;

    public PlanEmployeeAssignmentValidator(MultiStateInstanceUtil centralInstanceUtil, MdmsUtil mdmsUtil, HrmsUtil hrmsUtil, CommonUtil commonUtil, CampaignUtil campaignUtil, PlanEmployeeAssignmentRepository repository, Configuration config) {
        this.centralInstanceUtil = centralInstanceUtil;
        this.mdmsUtil = mdmsUtil;
        this.hrmsUtil = hrmsUtil;
        this.commonUtil = commonUtil;
        this.campaignUtil = campaignUtil;
        this.repository = repository;
        this.config = config;
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
        EmployeeResponse employeeResponse = hrmsUtil.fetchHrmsData(request.getRequestInfo(), planEmployeeAssignment.getEmployeeId(), planEmployeeAssignment.getTenantId());

        // Validate if a same assignment already exists
        validateEmployeeAssignmentExistence(request);

        // Validate if plan config id exists
        validatePlanConfigId(planConfigurations);

        // Validate if employee exists against HRMS
        validateEmployeeAgainstHRMS(employeeResponse);

        // Validate role of employee against HRMS
        validateRoleAgainstHRMS(planEmployeeAssignment, employeeResponse);

        // Validate if role of employee is a conflicting role
        validateRoleConflict(planEmployeeAssignment);

        // Validate campaign id, employee jurisdiction and highest root jurisdiction in case of National role
        validateCampaignDetails(planConfigurations.get(0).getCampaignId(), rootTenantId, request);

    }

    /**
     * Validates if the plan employee assignment for the provided details already exists
     *
     * @param request the employee assignment create request
     */
    private void validateEmployeeAssignmentExistence(PlanEmployeeAssignmentRequest request) {
        PlanEmployeeAssignment employeeAssignment = request.getPlanEmployeeAssignment();

        List<PlanEmployeeAssignment> planEmployeeAssignments = repository.search(PlanEmployeeAssignmentSearchCriteria.builder()
                .tenantId(employeeAssignment.getTenantId())
                .planConfigurationId(employeeAssignment.getPlanConfigurationId())
                .employeeId(employeeAssignment.getEmployeeId())
                .role(employeeAssignment.getRole())
                .build());

        if (!CollectionUtils.isEmpty(planEmployeeAssignments)) {
            throw new CustomException(PLAN_EMPLOYEE_ASSIGNMENT_ALREADY_EXIST_CODE, PLAN_EMPLOYEE_ASSIGNMENT_ALREADY_EXIST_MESSAGE);
        }
    }

    /**
     * Validates that employee with National role is assigned to the highest root jurisdiction only against MDMS
     *
     * @param planEmployeeAssignment The plan employee assignment provided in request
     * @param mdmsData               mdms data from mdms v2
     * @param campaignDetail         the campaign details for the corresponding campaign id
     */
    private void validateNationalRole(PlanEmployeeAssignment planEmployeeAssignment, Object mdmsData, CampaignDetail campaignDetail) {
        if (planEmployeeAssignment.getRole().contains(NATIONAL_ROLE)) {
            List<String> jurisdiction = planEmployeeAssignment.getJurisdiction();

            // Validate that National role employee should not have more than one jurisdiction assigned
            if (jurisdiction.size() > 1) {
                throw new CustomException(INVALID_EMPLOYEE_JURISDICTION_CODE, INVALID_EMPLOYEE_JURISDICTION_MESSAGE);
            }

            // Fetch the highest hierarchy for Microplan from MDMS
            String jsonPathForHighestHierarchy = JSON_ROOT_PATH + MDMS_ADMIN_CONSOLE_MODULE_NAME + DOT_SEPARATOR + MDMS_MASTER_HIERARCHY_CONFIG + HIERARCHY_CONFIG_FOR_MICROPLAN + DOT_SEPARATOR + HIGHEST_HIERARCHY_FIELD_FOR_MICROPLAN;

            List<String> highestHirarchyForPlan = null;
            try {
                log.info(jsonPathForHighestHierarchy);
                highestHirarchyForPlan = JsonPath.read(mdmsData, jsonPathForHighestHierarchy);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new CustomException(JSONPATH_ERROR_CODE, JSONPATH_ERROR_MESSAGE);
            }

            // Filter out the boundary details for the jurisdiction assigned to employee
            Boundary jurisdictionBoundary = campaignDetail.getBoundaries().stream()
                    .filter(boundary -> boundary.getCode().equals(planEmployeeAssignment.getJurisdiction().get(0)))
                    .findFirst().get();

            // Throw exception if jurisdiction assigned to National role employee is not the highest hierarchy
            if (!jurisdictionBoundary.getType().equals(highestHirarchyForPlan.get(0))) {
                throw new CustomException(INVALID_EMPLOYEE_JURISDICTION_CODE, INVALID_EMPLOYEE_JURISDICTION_MESSAGE);
            }
        }
    }

    /**
     * This method checks if the employee's role provided is a conflicting role against the role map.
     *
     * @param planEmployeeAssignment The plan employee assignment provided in request
     */
    private void validateRoleConflict(PlanEmployeeAssignment planEmployeeAssignment) {

        // Fetch the role mappings from the configuration
        Map<String, String> roleMap = config.getRoleMap();

        // Check if the role of the employee exists in the role map
        if (roleMap.containsKey(planEmployeeAssignment.getRole())) {

            // Fetch existing role assignments for the employee based on their tenant, planConfig Id, and employee ID
            // The search is conducted using the conflicting role
            List<PlanEmployeeAssignment> response = repository.search(PlanEmployeeAssignmentSearchCriteria.builder()
                    .tenantId(planEmployeeAssignment.getTenantId())
                    .planConfigurationId(planEmployeeAssignment.getPlanConfigurationId())
                    .employeeId(planEmployeeAssignment.getEmployeeId())
                    .role(roleMap.get(planEmployeeAssignment.getRole())).build());

            // If there are any conflicting assignments found, throw a custom exception
            if (!CollectionUtils.isEmpty(response)) {
                throw new CustomException(INVALID_EMPLOYEE_ROLE_CODE, INVALID_EMPLOYEE_ROLE_MESSAGE);
            }
        }
    }

    /**
     * This method validates the provided roles of the employee against HRMS
     *
     * @param planEmployeeAssignment The plan employee assignment provided in request
     * @param employeeResponse       The employee response from HRMS for the provided employeeId
     */
    private void validateRoleAgainstHRMS(PlanEmployeeAssignment planEmployeeAssignment, EmployeeResponse employeeResponse) {
        Set<String> rolesFromHRMS = employeeResponse.getEmployees().get(0).getUser().getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());

        if (!rolesFromHRMS.contains(planEmployeeAssignment.getRole())) {
            throw new CustomException(INVALID_EMPLOYEE_ROLE_CODE, INVALID_EMPLOYEE_ROLE_MESSAGE);
        }
    }


    /**
     * This method validates campaign id and employee's jurisdiction against project factory
     * If the employee has a national role, it validates that the employee has the highest root jurisdiction only
     *
     * @param campaignId                    the campaign id corresponding to the plan config id provided in the request
     * @param tenantId                      the tenant id provided in the request
     * @param planEmployeeAssignmentRequest the plan employee assignment request provided
     */
    private void validateCampaignDetails(String campaignId, String tenantId, PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest) {
        PlanEmployeeAssignment planEmployeeAssignment = planEmployeeAssignmentRequest.getPlanEmployeeAssignment();
        CampaignResponse campaignResponse = campaignUtil.fetchCampaignData(planEmployeeAssignmentRequest.getRequestInfo(), campaignId, tenantId);
        Object mdmsData = mdmsUtil.fetchMdmsData(planEmployeeAssignmentRequest.getRequestInfo(), tenantId);

        // Validate if campaign id exists against project factory
        validateCampaignId(campaignResponse);

        // Validate the provided jurisdiction for employee
        validateEmployeeJurisdiction(campaignResponse.getCampaignDetails().get(0), planEmployeeAssignment);

        // Validates highest root jurisdiction for National roles against MDMS
        validateNationalRole(planEmployeeAssignment, mdmsData, campaignResponse.getCampaignDetails().get(0));
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
     * This method validates if the employee provided in plan employee assignment request exist in HRMS
     *
     * @param employeeResponse The employee response from HRMS for provided employeeId
     */
    private void validateEmployeeAgainstHRMS(EmployeeResponse employeeResponse) {
        if (CollectionUtils.isEmpty(employeeResponse.getEmployees())) {
            throw new CustomException(INVALID_EMPLOYEE_ID_CODE, INVALID_EMPLOYEE_ID_MESSAGE);
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

        if (StringUtils.isEmpty(searchCriteria.getPlanConfigurationId())) {
            throw new CustomException(PLAN_CONFIG_ID_EMPTY_CODE, PLAN_CONFIG_ID_EMPTY_MESSAGE);
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
        EmployeeResponse employeeResponse = hrmsUtil.fetchHrmsData(request.getRequestInfo(), planEmployeeAssignment.getEmployeeId(), planEmployeeAssignment.getTenantId());

        // Validate if Plan employee assignment exists
        PlanEmployeeAssignment existingPlanEmployeeAssignment = validatePlanEmployeeAssignment(planEmployeeAssignment);

        // Validates if planConfig id and employee id provided in request is same in the existing record
        validateRequestAgainstExistingRecord(planEmployeeAssignment, existingPlanEmployeeAssignment);

        // Validate campaign id and employee jurisdiction
        validateCampaignDetails(planConfigurations.get(0).getCampaignId(), rootTenantId, request);

        // Validate role of employee against HRMS
        validateRoleAgainstHRMS(planEmployeeAssignment, employeeResponse);
    }

    /**
     * This method validates plan config id and employee id provided in the update request is same as in the existing record
     *
     * @param planEmployeeAssignment         the plan employee assignment from the update request
     * @param existingPlanEmployeeAssignment the plan employee assignment existing in the db
     */
    private void validateRequestAgainstExistingRecord(PlanEmployeeAssignment planEmployeeAssignment, PlanEmployeeAssignment existingPlanEmployeeAssignment) {

        // Validates plan config id against existing record
        if (!Objects.equals(planEmployeeAssignment.getPlanConfigurationId(), existingPlanEmployeeAssignment.getPlanConfigurationId())) {
            throw new CustomException(INVALID_PLAN_CONFIG_ID_CODE, INVALID_PLAN_CONFIG_ID_MESSAGE);
        }

        // Validates employee id against existing record
        if (!Objects.equals(planEmployeeAssignment.getEmployeeId(), existingPlanEmployeeAssignment.getEmployeeId())) {
            throw new CustomException(INVALID_EMPLOYEE_ID_CODE, INVALID_EMPLOYEE_ID_MESSAGE);
        }
    }

    /**
     * This method validates if the plan employee assignment id provided in the update request exists
     *
     * @param planEmployeeAssignment The plan employee assignment details from the request
     * @return the plan employee assignment from db which is to be updated
     */
    private PlanEmployeeAssignment validatePlanEmployeeAssignment(PlanEmployeeAssignment planEmployeeAssignment) {
        if (ObjectUtils.isEmpty(planEmployeeAssignment.getId())) {
            throw new CustomException(PLAN_EMPLOYEE_ASSIGNMENT_ID_EMPTY_CODE, PLAN_EMPLOYEE_ASSIGNMENT_ID_EMPTY_MESSAGE);
        }

        // Validates the existence of plan employee assignment
        List<PlanEmployeeAssignment> planEmployeeAssignments = repository.search(PlanEmployeeAssignmentSearchCriteria.builder()
                .tenantId(planEmployeeAssignment.getTenantId())
                .id(planEmployeeAssignment.getId())
                .build());

        if (CollectionUtils.isEmpty(planEmployeeAssignments)) {
            throw new CustomException(INVALID_PLAN_EMPLOYEE_ASSIGNMENT_ID_CODE, INVALID_PLAN_EMPLOYEE_ASSIGNMENT_ID_MESSAGE);
        }

        return planEmployeeAssignments.get(0);
    }
}
